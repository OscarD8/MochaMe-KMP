package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.utils.interfaces.TimeProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.HLC.Companion.APP_RELEASE_TIME
import com.mochame.sync.api.models.HLC.Companion.MAX_COUNTER_INT
import com.mochame.sync.api.models.HLC.Companion.MAX_DRIFT
import com.mochame.sync.api.models.HLC.Companion.ONE_DAY
import com.mochame.sync.api.models.instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant


/**
 * Implements Non-Blocking Busy-Wait and NodeID Re-stamping.
 */
@Single(binds = [HlcFactory::class])
internal class EngineHlcFactory(
    private val timeUtils: TimeProvider,
    logger: Logger
) : HlcFactory {

    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "HLCFac"
    )

    private data class FactoryState(
        val lastHlc: HLC,
        val nodeId: String,
    )

    private val stateMutex = Mutex()
    private var state: FactoryState? = null

    override suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC =
        stateMutex.withLock {
            state?.let {
                logger.w { "An attempt was made to rehydrate the HLC, finding an existing state: ${it.lastHlc} to $lastKnownHlc." }
                return@withLock it.lastHlc
            }

            val wallClock = timeUtils.now()
            val hydrationHlc = reconcileHlc(wallClock, lastKnownHlc, currentNodeId)

            state = FactoryState(hydrationHlc, currentNodeId)

            logger.d { "HLC hydrated: $hydrationHlc" }
            hydrationHlc
        }

    /**
     * Generates the next monotonic HLC.
     * Replaces Exception-on-Overflow with a Yield.
     */
    override suspend fun getNextHlc(): HLC {
        var yieldCount = 0

        while (true) {
            val result = stateMutex.withLock {
                val currentState = state ?: run {
                    logger.e { "Attempt to getNextHlc() made during null FactoryState. Check boot logs?" }
                    throw MochaException.Policy.CausalityViolation("Cannot fetch a timestamp with no pre-existing state.")
                }

                val wallClock = timeUtils.now().toEpochMilliseconds()

                // Compute the next tick logically
                val nextHlc = calculateNextTick(
                    wallClock,
                    currentState.lastHlc,
                    currentState.nodeId
                )

                // Local State Update
                nextHlc?.also {
                    state = currentState.copy(lastHlc = it)
                    logger.v { "HLC tick: $it" }
                }
            }

            if (result != null) {
                if (yieldCount > 0) {
                    logger.w { "Recovered from counter exhaustion. Yield count: $yieldCount." }
                }
                return result
            }

            yieldCount++
            yield()
        }
    }

    /**
     * Updates the factory state with an incoming HLC from a remote source.
     * This ensures causality: any HLC generated after this call will be
     * strictly greater than the witnessed HLC.
     */
    override suspend fun witness(remoteHlc: HLC) = stateMutex.withLock {
        val currentState = state ?: run {
            logger.w { "Attempt made to witness remote HLC against no internal state." }
            return@withLock
        }
        val wallClock = timeUtils.now()

        // Phase 1: Determine the base logical time across physical and remote sources
        val (provisionalTs, provisionalCount) = computeCausalTime(
            wallClock.toEpochMilliseconds(),
            remoteHlc,
            currentState.lastHlc
        )

        // Phase 2: Apply 16-bit overflow logic if necessary
        val (finalTs, finalCount) = applyOverflow(provisionalTs, provisionalCount)

        // Phase 3: Ensure the resulting jump is within safety boundaries
        validateDrift(Instant.fromEpochMilliseconds(finalTs), wallClock)

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
    override fun isValid(hlc: HLC): Boolean {
        return when {
            hlc.instant < APP_RELEASE_TIME -> false
            (hlc.instant - timeUtils.now()) > MAX_DRIFT -> false
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
     * historic HLC, it takes the historic counter, and assigns the reconciled HLC the [NodeContext.nodeId] of the current device.
     * @throws [MochaException.Persistent.ClockSkew] Local device has drifted below the floor. Or history is more than one minute into the future of the local clock.
     */
    private fun reconcileHlc(
        wallClock: Instant,
        history: HLC?,
        currentNodeId: String
    ): HLC {
        return when {
            // Case 1: Hard Floor (e.g. System clock is set to 1970)
            wallClock < APP_RELEASE_TIME -> {
                val drift = APP_RELEASE_TIME - wallClock
                logger.e { "Clock Skew: System time ($wallClock) is ${drift.inWholeSeconds}s behind floor ($APP_RELEASE_TIME)" }
                throw MochaException.Persistent.ClockSkew(drift)
            }

            // Case 2: New Install
            history == null  -> {
                logger.i { "Hydration: New Install detected. Starting at $wallClock" }
                HLC(wallClock, 0, currentNodeId)
            }

            // Case 3: History creating future drift (DB is > 1 minute in the future)
            history.instant - wallClock > MAX_DRIFT -> {
                val drift = history.instant - wallClock
                logger.e { "Clock Skew: History is ${drift.inWholeSeconds}s in the future against local [$wallClock]." }
                throw MochaException.Persistent.ClockSkew(drift)
            }

            // Case 4: Take the latest known time
            else -> {
                val timeDiff = wallClock - history.instant

                if (timeDiff > 365.days) {
                    logger.w { "Future Jump: Device is ${timeDiff.inWholeDays} days ahead of history." }
                }

                val finalInstant = maxOf(wallClock, history.instant)
                val finalCounter = if (finalInstant == history.instant) history.count else 0

                HLC(finalInstant, finalCounter, currentNodeId).also {
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
            wallClock > last.ts -> HLC(wallClock, 0, nodeId)

            last.count < MAX_COUNTER_INT -> HLC(last.ts, last.count + 1, nodeId)

            else -> null
        }
    }

    /**
     * Computes the maximum logical time between current device time,
     * local history, and remote truth.
     */
    private fun computeCausalTime(
        wallClock: Long,
        remote: HLC,
        local: HLC
    ): Pair<Long, Int> {
        val newTs = maxOf(wallClock, local.ts, remote.ts)

        val newCount = when {
            newTs == local.ts && newTs == remote.ts -> maxOf(
                local.count,
                remote.count
            ) + 1

            newTs == remote.ts -> remote.count + 1

            newTs == local.ts -> local.count + 1

            else -> 0
        }

        return newTs to newCount
    }

    /**
     * Enforces the 16-bit counter limit. If the counter overflows,
     * it increments the timestamp by 1ms.
     */
    private fun applyOverflow(ts: Long, count: Int): Pair<Long, Int> {
        return if (count > MAX_COUNTER_INT) {
            (ts + 1) to 0
        } else {
            ts to count
        }
    }

    /**
     * Validates that the newly calculated logical time does not drift too
     * far into the future compared to physical reality.
     */
    private fun validateDrift(finalInstant: Instant, wallClock: Instant) {
        val drift = finalInstant - wallClock

        if (drift > MAX_DRIFT) throw MochaException.Persistent.ClockSkew(drift)

        if (drift.inWholeMilliseconds > 0) {
            logger.w { "HLC Drift Detected: ${drift}. Advancing local HLC to match remote truth." }
        }
    }

}
