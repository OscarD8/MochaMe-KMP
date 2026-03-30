package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.Logger
import com.mochame.app.domain.system.exceptions.MochaException
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
) {
    /**
     * Converts the HLC to its sortable string representation.
     */
    override fun toString(): String = "$ts:$count:$nodeId"

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

    private val MIN_VALID_TIME = 1740787200000L // March 2026
    private val MAX_COUNTER = 65535             // 16-bit limit

    fun hydrate(lastKnownHlc: String?, currentNodeId: String): HLC =
        synchronized(this) {
            if (isHydrated) return@synchronized lastHlc!!

            val wallClock = dateTimeUtils.now().toEpochMilliseconds()

            // 1. Guard against hardware clock resets
            if (wallClock < MIN_VALID_TIME) {
                val drift = MIN_VALID_TIME - wallClock
                hlcLog.e { "Clock Skew: System time ($wallClock) is $drift ms behind floor ($MIN_VALID_TIME)" }
                throw MochaException.Persistent.ClockSkew(drift)
            }

            // 2. Sanitize DB history
            val history = lastKnownHlc?.let { HLC.parse(lastKnownHlc ) }

            // 3. Reconcile
            val initialHlc = when {
                history == null -> {
                    hlcLog.i { "Hydration: New Install detected. Starting at $wallClock" }
                    HLC(wallClock, 0, currentNodeId)
                }

                history.ts >= wallClock -> {
                    hlcLog.w { "Clock Catch-up: Local clock ($wallClock) is behind DB history (${history.ts}). Pinning to history." }
                    history.copy(nodeId = currentNodeId)
                }

                else -> HLC(wallClock, 0, currentNodeId)
            }

            this.nodeId = currentNodeId
            this.lastHlc = initialHlc
            this.isHydrated = true

            hlcLog.d { "HLC Initialized: $initialHlc" }
            return initialHlc
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
                        return updateAndReturn(HLC(wallClock, 0, currentId))
                    }

                    // Case B: Same millisecond, increment counter
                    wallClock <= last.ts && last.count < MAX_COUNTER -> {
                        return updateAndReturn(HLC(last.ts, last.count + 1, currentId))
                    }

                    // Case C: Counter Exhaustion (Safety Valve)
                    else -> {
                        if (yieldCount == 0) {
                            hlcLog.w { "Counter Exhausted: Stalling thread at $wallClock until clock ticks. Counter at $MAX_COUNTER." }
                        }
                        // Logic falls through to the 'yield()' below the synchronized block
                    }
                }
            }
            // If we hit Case C, we suspend and let the clock tick.
            // This prevents a crash during high-volume operations.
            yieldCount++
            yield()
        }
    }

    private fun updateAndReturn(newHlc: HLC): HLC {
        this.lastHlc = newHlc
        return newHlc
    }

}
