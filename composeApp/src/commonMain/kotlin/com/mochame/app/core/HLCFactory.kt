package com.mochame.app.core

/**
 * Outcomes for the [HlcFactory] startup sequence.
 */
sealed class HydrationResult {
    /** Factory is ready; initialized with valid history from the database. */
    object Success : HydrationResult()
    /** Factory is ready; no prior history found (fresh install). Starts from system time. */
    object NewInstall : HydrationResult()
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
 * @param nodeId The unique ID for this device.
 * @param dateTimeUtils Provider for system wall-clock time.
 */
class HlcFactory(
    private val nodeId: String,
    private val dateTimeUtils: DateTimeUtils
) {
    private var isHydrated = false
    private var lastHlc = HLC(0, 0, nodeId)

    /** * The "Hard Floor": March 2026. Prevents 1970/NTP-failure poisoning.
     */
    private val MIN_VALID_TIME = 1740787200000L

    /** * Maximum allowed clock drift (1 minute) before triggering a Skew Exception.
     */
    private val MAX_FUTURE_DRIFT = 60_000L

    /**
     * Seeds the clock with the highest known timestamp in the database.
     * * This must be called during app boot. If this does not return [HydrationResult.Success]
     * or [HydrationResult.NewInstall], the application must block write operations.
     *
     * @param remoteHlcString The highest 'lastModified' value found across all local tables.
     * @return [HydrationResult] indicating if the factory is safe to use.
     */
    fun updateMax(remoteHlcString: String?): HydrationResult {
        val wallClock = dateTimeUtils.now().toEpochMilliseconds()

        if (wallClock < MIN_VALID_TIME) {
            return HydrationResult.ClockSkewDetected(MIN_VALID_TIME - wallClock)
        }

        if (remoteHlcString.isNullOrBlank()) {
            isHydrated = true
            return HydrationResult.NewInstall
        }

        return synchronized(this) {
            val incoming = try {
                HLC.parse(remoteHlcString)
            } catch (e: Exception) {
                return HydrationResult.InvalidData(e)
            }

            val drift = incoming.ts - wallClock
            if (drift > MAX_FUTURE_DRIFT) {
                return HydrationResult.ClockSkewDetected(drift)
            }

            // 3. Move the needle forward
            if (incoming.ts > lastHlc.ts || (incoming.ts == lastHlc.ts && incoming.count > lastHlc.count)) {
                lastHlc = incoming
            }

            isHydrated = true
            HydrationResult.Success
        }
    }

    fun now(): HLC {
        check(isHydrated) {
            "HlcFactory is cold. Call updateMax() before generating timestamps."
        }

        val systemTime = dateTimeUtils.now().toEpochMilliseconds()

        lastHlc = synchronized(this) {
            // This is the core logic: even if systemTime is 1970,
            // we use the 'lastHlc.ts' we hydrated from the DB.
            val newTs = maxOf(systemTime, lastHlc.ts)
            val newCount = if (newTs == lastHlc.ts) lastHlc.count + 1 else 0
            HLC(newTs, newCount, nodeId)
        }
        return lastHlc
    }
}


