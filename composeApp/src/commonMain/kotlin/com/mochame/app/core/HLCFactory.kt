package com.mochame.app.core

import co.touchlab.kermit.Logger
import kotlinx.coroutines.yield

/**
 * Outcomes for the [HlcFactory] startup sequence.
 */
sealed class HydrationResult {
    /** Factory is ready; initialized with valid history from the database. */
    data class Success(val hlc: HLC) : HydrationResult()
    /** Database recovery failed; the stored HLC string is unparseable. */
    data class InvalidData(val error: Throwable) : HydrationResult()
    /** * System clock is invalid.
     * Either the phone is set before the app launch or into the future.
     * @param driftMs The difference in milliseconds between the system clock and the required time.
     */
    data class ClockSkewDetected(val driftMs: Long, val error: Throwable) : HydrationResult()
}


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
         * @throws HlcParseException if not valid.
         */
        fun parse(hlcString: String): HLC {
            val parts = hlcString.split(":")

            if (parts.size != 3) throw HlcParseException(hlcString)

            val ts = parts[0].toLongOrNull()
                ?: throw HlcParseException(hlcString)

            val count = parts[1].toIntOrNull()
                ?: throw HlcParseException(hlcString)

            val nodeId = parts[2].takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Invalid nodeId")

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
    private val hlcLog = logger.appendTag("HLC")

    @Volatile
    private var nodeId: String? = null
    @Volatile
    private var lastHlc: HLC? = null
    @Volatile
    private var isHydrated = false

    private val MIN_VALID_TIME = 1740787200000L // March 2026
    private val MAX_COUNTER = 65535             // 16-bit limit

    fun hydrate(lastKnownHlc: String?, currentNodeId: String): HydrationResult =
        synchronized(this) {
            if (isHydrated) return@synchronized HydrationResult.Success(lastHlc!!)

            val wallClock = dateTimeUtils.now().toEpochMilliseconds()

            // 1. Guard against hardware clock resets
            if (wallClock < MIN_VALID_TIME) {
                val drift = MIN_VALID_TIME - wallClock
                hlcLog.e { "Clock Skew: System time ($wallClock) is $drift ms behind floor ($MIN_VALID_TIME)" }
                return@synchronized HydrationResult.ClockSkewDetected(
                    drift,
                    IllegalStateException("Clock skew under minimum constraint.")
                )
            }

            // 2. Sanitize DB history
            val history = lastKnownHlc?.let { hlcStr ->
                runCatching { HLC.parse(hlcStr) }.getOrElse { error ->
                    hlcLog.e { error.message!! }
                    return@synchronized HydrationResult.InvalidData(error)
                }
            }

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
            return@synchronized HydrationResult.Success(initialHlc)
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
