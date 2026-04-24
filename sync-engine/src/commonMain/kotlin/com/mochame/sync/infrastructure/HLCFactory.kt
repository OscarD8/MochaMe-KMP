package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.utils.DateTimeUtils
import com.mochame.utils.exceptions.MochaException
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single


/**
 * A Hybrid Logical Clock (HLC) timestamp that provides strict ordering across distributed nodes.
 *
 * Serialized format: "ts:count:nodeId"
 *
 * @property ts Wall-clock time in milliseconds.
 * @property count Logical counter used to distinguish events occurring in the same millisecond.
 * @property nodeId Unique identifier for the originating device to prevent collisions.
 */
@Serializable
data class HLC(
    val ts: Long,      // Physical wall-clock time
    val count: Int,     // Logical counter for same-ms events
    val nodeId: String,  // Unique device ID (prevents collisions)
) : Comparable<HLC> {

    /**
     * Converts the HLC to its sortable string representation.
     * TS: 15 digits
     * Count: 5 digits (maps to MAX_COUNTER 65535)
     */
    override fun toString(): String {
        val paddedTs = ts.toString().padStart(15, '0')
        val paddedCount = count.toString().padStart(5, '0')
        return "$paddedTs:$paddedCount:$nodeId"
    }

    override fun compareTo(other: HLC): Int {
        return compareBy<HLC> { it.ts }
            .thenBy { it.count }
            .thenBy { it.nodeId }
            .compare(this, other)
    }

    companion object {
        /**
         * Safely parses a serialized HLC string.
         * @throws com.mochame.app.domain.exceptions.MochaException.Persistent.HlcParseException if not valid.
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
@Single
class HlcFactory(
    private val dateTimeUtils: DateTimeUtils,
    logger: Logger
) {
    
    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "HLC"
    )

    private data class FactoryState(
        val lastHlc: HLC,
        val nodeId: String,
        val isHydrated: Boolean = true
    )

    private val stateMutex = Mutex()
    private var state: FactoryState? = null

    private val APP_RELEASE_MS = 1740787200000L // March 2026
    private val MAX_DRIFT_MS = 60_000L
    private val MAX_COUNTER = 65535             // 16-bit limit
    private val ONE_DAY_MS = 86_400_000L


    suspend fun hydrate(lastKnownHlc: String?, currentNodeId: String): HLC =
        stateMutex.withLock {
            state?.let {
                logger.w { "An attempt was made to rehydrate the HLC from ${it.lastHlc} to $lastKnownHlc." }
                return@withLock it.lastHlc
            }

            val wallClock = dateTimeUtils.now().toEpochMilliseconds()
            val history = lastKnownHlc?.let { HLC.parse(lastKnownHlc) }
            val hydrationHlc = reconcileHlc(wallClock, history, currentNodeId)

            state = FactoryState(hydrationHlc, currentNodeId)

            logger.d { "HLC hydrated: $hydrationHlc" }
            hydrationHlc
        }

    /**
     * Generates the next monotonic HLC.
     * Replaces Exception-on-Overflow with a Busy-Wait yield.
     */
    suspend fun getNextHlc(): HLC {
        var yieldCount = 0

        // Phase 1: Orchestration Loop
        while (true) {
            val result = stateMutex.withLock {
                val currentState =
                    state ?: throw MochaException.Policy.CausalityViolation(
                        "Unable to provide a timestamp. Potential boot issue."
                    ).also { logger.e { it.message } }

                val wallClock = dateTimeUtils.now().toEpochMilliseconds()

                // Phase 2: Compute the next tick logically
                val nextHlc = calculateNextTick(
                    wallClock,
                    currentState.lastHlc,
                    currentState.nodeId
                )

                // Phase 3: Local State Update
                nextHlc?.also {
                    state = currentState.copy(lastHlc = it)
                    logger.v { "HLC tick: $it" }
                }
            }

            if (result != null) return result

            // Phase 4: Handle Exhaustion
            if (yieldCount++ == 0) {
                logger.w { "Counter Exhausted at ${dateTimeUtils.now()}. Stalling until clock ticks." }
            }
            yield()
        }
    }

    /**
     * Updates the factory state with an incoming HLC from a remote source.
     * This ensures causality: any HLC generated after this call will be
     * strictly greater than the witnessed HLC.
     */
    suspend fun witness(remoteHlc: HLC) = stateMutex.withLock {
        val currentState = state ?: run {
            logger.w { "Attempt made to witness remote HLC against no internal state." }
            return@withLock
        }
        val wallClock = dateTimeUtils.now().toEpochMilliseconds()

        // Phase 1: Determine the base logical time across physical and remote sources
        val (provisionalTs, provisionalCount) = computeCausalTime(
            wallClock,
            remoteHlc,
            currentState.lastHlc
        )

        // Phase 2: Apply 16-bit overflow logic if necessary
        val (finalTs, finalCount) = applyOverflow(provisionalTs, provisionalCount)

        // Phase 3: Ensure the resulting jump is within safety boundaries
        validateDrift(finalTs, wallClock)

        state = currentState.copy(
            lastHlc = HLC(finalTs, finalCount, currentState.nodeId)
        )

        logger.v { "HLC witnessed remote: $remoteHlc -> New internal state: ${state?.lastHlc}" }
    }

    /**
     * Returns true if the HLC string is syntactically valid and
     * falls within the causal bounds.
     * This can be used as an extra safety check for
     * data that may have got around the hydration procedure, and
     * is sitting in local storage as corrupted data.
     */
    fun isValid(hlc: HLC): Boolean {
        val wallClock = dateTimeUtils.now().toEpochMilliseconds()

        return when {
            hlc.ts < APP_RELEASE_MS -> false              // Floor violation
            hlc.ts - wallClock > MAX_DRIFT_MS -> false      // Future drift violation
            else -> true
        }
    }

    // --- HELPERS ---

    /**
     * Reconciles a fetched HLC against the current device state, protecting against clock skew
     * and logs any significant jump from the historic HLC to the current wall clock. The returned
     * HLC can be used to hydrate the factory, ensuring all local operations use it as a baseline.
     *
     * @return Verified [HLC] that has acceptable/no clock skew. If the current clock matches the
     * historic HLC, it takes the historic counter, and assigns the reconciled HLC the [com.mochame.metadata.GlobalSettingsEntity.id] of the current device.
     * @throws [com.mochame.app.domain.exceptions.MochaException.Persistent.ClockSkew] Local device has drifted below the floor. Or history is more than one minute into the future of the local clock.
     */
    private fun reconcileHlc(
        wallClock: Long,
        history: HLC?,
        currentNodeId: String
    ): HLC {
        return when {
            // Case 1: Hard Floor (e.g. System clock is set to 1970)
            wallClock < APP_RELEASE_MS -> {
                val driftSec = (APP_RELEASE_MS - wallClock) / 1000
                logger.e { "Clock Skew: System time ($wallClock) is $driftSec seconds behind floor ($APP_RELEASE_MS)" }
                throw MochaException.Persistent.ClockSkew(driftSec, "seconds")
            }

            // Case 2: New Install
            history == null -> {
                logger.i { "Hydration: New Install detected. Starting at $wallClock" }
                HLC(wallClock, 0, currentNodeId)
            }

            // Case 3: Poisoned History creating future drift (DB is > 1 minute in the future)
            history.ts - wallClock > MAX_DRIFT_MS -> {
                val driftSec = (history.ts - wallClock) / 1000
                logger.e { "Clock Skew: History is $driftSec seconds in the future against local [$wallClock]." }
                throw MochaException.Persistent.ClockSkew(driftSec, "seconds")
            }

            // Case 4: Take the latest known time
            else -> {
                if (wallClock - history.ts > (ONE_DAY_MS * 365)) {
                    logger.w { "Future Jump: Device is ${(wallClock - history.ts) / ONE_DAY_MS} days ahead of history." }
                }

                val finalTs = maxOf(wallClock, history.ts)
                val finalCounter = if (finalTs == history.ts) history.count else 0

                HLC(finalTs, finalCounter, currentNodeId).also {
                    logger.i { "Successfully reconciled new HLC: [$it] with incoming [$history]." }
                }
            }
        }
    }

    /**
     * For determining the next HLC state.
     * Returns null if the 16-bit counter is exhausted for the current millisecond.
     */
    private fun calculateNextTick(wallClock: Long, last: HLC, nodeId: String): HLC? {
        return when {
            // Case A: Physical time has progressed
            wallClock > last.ts -> HLC(wallClock, 0, nodeId)

            // Case B: Same millisecond, space remains in the 16-bit counter
            last.count < MAX_COUNTER -> HLC(last.ts, last.count + 1, nodeId)

            // Case C: Counter exhaustion; requires a physical clock tick
            else -> null
        }
    }

    /**
     * Computes the maximum logical time between local physical reality,
     * local history, and remote truth.
     */
    private fun computeCausalTime(
        wallClock: Long,
        remote: HLC,
        local: HLC
    ): Pair<Long, Int> {
        val newTs = maxOf(maxOf(wallClock, local.ts), remote.ts)

        val newCount = when {
            newTs == local.ts && newTs == remote.ts -> maxOf(
                local.count,
                remote.count
            ) + 1

            newTs == remote.ts -> remote.count + 1
            newTs == local.ts -> local.count + 1
            else -> 0 // Physical wall clock is strictly ahead
        }

        return newTs to newCount
    }

    /**
     * Enforces the 16-bit counter limit. If the counter overflows,
     * it increments the timestamp by 1ms.
     */
    private fun applyOverflow(ts: Long, count: Int): Pair<Long, Int> {
        return if (count > MAX_COUNTER) {
            (ts + 1) to 0
        } else {
            ts to count
        }
    }

    /**
     * Validates that the newly calculated logical time does not drift too
     * far into the future compared to physical reality.
     */
    private fun validateDrift(finalTs: Long, wallClock: Long) {
        val drift = finalTs - wallClock

        if (drift > MAX_DRIFT_MS) {
            throw MochaException.Persistent.ClockSkew(drift / 1000, "seconds")
        }

        if (drift > 0) {
            logger.w { "HLC Drift Detected: ${drift}ms. Advancing local HLC to match remote truth." }
        }
    }

}
