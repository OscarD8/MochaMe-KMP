package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.Logger
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.utils.DateTimeUtils
import kotlinx.coroutines.yield


/**
 * A Hybrid Logical Clock (HLC) timestamp that provides strict ordering across distributed nodes.
 *
 * Serialized format: "ts:count:nodeId"
 *
 * @property ts Wall-clock time in milliseconds.
 * @property count Logical counter used to distinguish events occurring in the same millisecond.
 * @property nodeId Unique identifier for the originating device to prevent collisions.
 */
data class HLC(
    val ts: Long,      // Physical wall-clock time
    val count: Int,     // Logical counter for same-ms events
    val nodeId: String,  // Unique device ID (prevents collisions)
) : Comparable<HLC> {
    /**
     * Converts the HLC to its sortable string representation.
     */
    override fun toString(): String = "$ts:$count:$nodeId"

    override fun compareTo(other: HLC): Int {
        return compareBy<HLC> { it.ts }
            .thenBy { it.count }
            .thenBy { it.nodeId }
            .compare(this, other)
    }

    companion object {
        /**
         * Safely parses a serialized HLC string.
         * @throws com.mochame.app.utils.HlcParseException if not valid.
         */
        fun parse(hlcString: String): HLC {
            val parts = hlcString.split(":")

            if (parts.size != 3) throw MochaException.Persistent.HlcParseException("Format mismatch: $hlcString")

            val ts = parts[0].toLongOrNull()
                ?: throw MochaException.Persistent.HlcParseException("Invalid timestamp: ${parts[0]}")

            val count = parts[1].toIntOrNull()
                ?: throw MochaException.Persistent.HlcParseException("Invalid counter: ${parts[1]}")

            val nodeId = parts[2].takeIf { it.isNotBlank() }
                ?: throw MochaException.Persistent.HlcParseException("NodeId is missing")

            return HLC(ts, count, nodeId)
        }

        /**
         * Necessary as a DailyContext state comes down from the UI layer.
         */
        val EMPTY = HLC(0, 0, "init")
    }
}

/**
 * Implements Non-Blocking Busy-Wait and NodeID Re-stamping.
 */
class HlcFactory(
    private val dateTimeUtils: DateTimeUtils,
    logger: Logger
) {
    companion object {
        private const val TAG = "HLC"
    }

    private val hlcLog = logger.appendTag(TAG)

    @Volatile
    private var nodeId: String? = null

    @Volatile
    private var lastHlc: HLC? = null

    @Volatile
    private var isHydrated = false

    private val APP_RELEASE_MS = 1740787200000L // March 2026
    private val ONE_DAY_MS = 86_400_000L
    private val MAX_COUNTER = 65535             // 16-bit limit


    fun hydrate(lastKnownHlc: String?, currentNodeId: String): HLC =
        synchronized(this) {
            if (isHydrated) {
                hlcLog.w { "An attempt was made to rehydrate the HLC from $lastHlc to $lastKnownHlc." }
                return@synchronized lastHlc!!
            }

            val wallClock = dateTimeUtils.now().toEpochMilliseconds()

            val history = lastKnownHlc?.let { HLC.parse(lastKnownHlc) }

            val hydrationHlc = reconcileHlc(wallClock, history, currentNodeId)

            this.nodeId = currentNodeId
            this.lastHlc = hydrationHlc
            this.isHydrated = true

            hlcLog.d { "HLC Initialized: $hydrationHlc" }
            return hydrationHlc
        }

    /**
     * Generates the next monotonic HLC.
     * Replaces Exception-on-Overflow with a Busy-Wait yield.
     */
    suspend fun getNextHlc(): HLC {
        var yieldCount = 0
        // We use a loop to handle the rare Case C: Counter Overflow
        while (true) {
            synchronized(this) {

                val currentId = nodeId!!
                val last = lastHlc!!
                val wallClock = dateTimeUtils.now().toEpochMilliseconds()

                when {
                    // Case A: Normal forward progress
                    wallClock > last.ts -> {
                        hlcLog.v { "Standard run. [$wallClock > ${last.ts}]."}
                        return updateAndReturn(HLC(wallClock, 0, currentId))
                    }

                    // Case B: Same millisecond, increment counter
                    wallClock <= last.ts && last.count < MAX_COUNTER -> {
                        hlcLog.d { "$wallClock <= ${last.ts}. Counter increase [${last.count+1}]."}
                        return updateAndReturn(HLC(last.ts, last.count + 1, currentId))
                    }

                    // Case C: Counter Exhaustion
                    else -> {
                        if (yieldCount == 0) {
                            hlcLog.w { "Counter Exhausted: Stalling thread at $wallClock until clock ticks. Counter at $MAX_COUNTER." }
                        }
                    }
                }
            }
            // If we hit Case C, we suspend and let the clock tick.
            yieldCount++
            hlcLog.d { "Yield count at $yieldCount" }
            yield()
        }
    }

    // --- HELPERS ---

    private fun reconcileHlc(
        wallClock: Long,
        history: HLC?,
        currentNodeId: String
    ): HLC {
        return when {
            // Case 1: Hard Floor (e.g. System clock is set to 1970)
            wallClock < APP_RELEASE_MS -> {
                val drift = APP_RELEASE_MS - wallClock
                hlcLog.e { "Clock Skew: System time ($wallClock) is $drift ms behind floor ($APP_RELEASE_MS)" }
                throw MochaException.Persistent.ClockSkew(drift/ONE_DAY_MS)
            }

            // Case 2: New Install
            history == null -> {
                hlcLog.i { "Hydration: New Install detected. Starting at $wallClock" }
                HLC(wallClock, 0, currentNodeId)
            }

            // Case 3: Poisoned History creating future drift (DB is > 24h in the future)
            history.ts - wallClock > ONE_DAY_MS -> {
                val drift = history.ts - wallClock
                hlcLog.e { "Clock Skew: Incoming HLC [${history.ts}] has future drift " +
                        "against local device clock [${wallClock}] (+ ${drift / ONE_DAY_MS}days)."
                }
                throw MochaException.Persistent.ClockSkew(drift/ONE_DAY_MS)
            }

            // Case 4: Take the latest known time
            else -> {
                if (wallClock - history.ts > (ONE_DAY_MS * 365)) {
                    hlcLog.w {
                        "Future Jump: Local Device [$wallClock] " +
                                "is ${(wallClock - history.ts) / ONE_DAY_MS} days ahead of history [${history.ts}]."
                    }
                }

                val finalTs = maxOf(wallClock, history.ts)
                val finalCounter = if (finalTs == history.ts) history.count else 0

                val newHlc = HLC(finalTs, finalCounter, currentNodeId)
                hlcLog.i { "Successfully reconciled new HLC: [$newHlc] with incoming [$history]." }
                newHlc
            }
        }
    }

    private fun updateAndReturn(newHlc: HLC): HLC {
        this.lastHlc = newHlc
        return newHlc
    }

}
