package com.mochame.app.core

/**
 * Outcomes for the [HlcFactory] startup sequence.
 */
sealed class HydrationResult {
    /** Factory is ready; initialized with valid history from the database. */
    data class Success(val hlc: HLC) : HydrationResult()
    /** Factory is ready; no prior history found (fresh install). Starts from system time. */
    data class NewInstall(val hlc: HLC?) : HydrationResult()
    /** Database recovery failed; the stored HLC string is unparseable. */
    data class InvalidData(val error: Throwable) : HydrationResult()
    /** * System clock is invalid.
     * Either the phone is set before the app launch or into the future.
     * @param driftMs The difference in milliseconds between the system clock and the required time.
     */
    data class ClockSkewDetected(val driftMs: Long) : HydrationResult()
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
         * @throws IllegalArgumentException if the format is invalid.
         */
        fun parse(hlcString: String): HLC {
            val parts = hlcString.split(":")
            if (parts.size != 3) throw IllegalArgumentException("Invalid HLC format: $hlcString")

            val ts = parts[0].toLongOrNull() ?: throw IllegalArgumentException("Invalid timestamp")
            val count = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid counter")
            val nodeId = parts[2].takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Invalid nodeId")

            return HLC(ts, count, nodeId)
        }
    }
}


/**
 * Generates monotonically increasing timestamps for local-first data integrity.
 * * This component prevents issues where records appear to happen in the past
 * or future due to manual clock changes or dead batteries.
 *
 * @param dateTimeUtils Provider for system wall-clock time.
 */
class HlcFactory(
    private val dateTimeUtils: DateTimeUtils
) {
    @Volatile private var nodeId: String? = null
    @Volatile private var lastHlc: HLC? = null
    @Volatile private var isHydrated = false

    private val MAX_COUNTER = 65535 // 16-bit counter limit for sanity

    /**
     * The Handover: Locks the factory to a specific identity and restores state.
     */
    fun hydrate(existingHlcString: String?, nodeId: String): HydrationResult {
        return synchronized(this) {
            if (isHydrated) return@synchronized HydrationResult.Success(lastHlc!!)

            this.nodeId = nodeId
            val wallClock = dateTimeUtils.now().toEpochMilliseconds()

            val initialHlc = if (existingHlcString.isNullOrBlank()) {
                // No previous state, start with wall clock
                this.lastHlc = HLC(ts = wallClock, count = 0, nodeId = nodeId)
                return@synchronized HydrationResult.NewInstall(lastHlc)
            } else {
                val incoming = HLC.parse(existingHlcString)
                // Monotonicity Guard: If DB is ahead of System Clock, stay with DB time
                if (incoming.ts >= wallClock) {
                    incoming
                } else {
                    HLC(ts = wallClock, count = 0, nodeId = nodeId)
                }
            }

            this.lastHlc = initialHlc
            this.isHydrated = true
            HydrationResult.Success(initialHlc)
        }
    }

    /**
     * Generates the next monotonic HLC.
     */
    fun getNextHlc(): HLC = synchronized(this) {
        val currentId = nodeId ?: throw IllegalStateException("HlcFactory: Missing NodeID (Call hydrate first)")
        val last = lastHlc ?: throw IllegalStateException("HlcFactory: Missing LastHLC")

        val wallClock = dateTimeUtils.now().toEpochMilliseconds()

        val nextTimestamp = when {
            // Case A: Wall clock has moved forward. Reset counter to 0.
            wallClock > last.ts -> {
                HLC(ts = wallClock, count = 0, nodeId = currentId)
            }
            // Case B: Wall clock is behind or equal. Increment logical counter.
            else -> {
                if (last.count >= MAX_COUNTER) {
                    // This is the "Clock Exhaustion" check mentioned by your auditor.
                    throw IllegalStateException("HLC Counter Overflow: Too many events in a single millisecond.")
                }
                HLC(ts = last.ts, count = last.count + 1, nodeId = currentId)
            }
        }

        lastHlc = nextTimestamp
        return@synchronized nextTimestamp
    }
}

