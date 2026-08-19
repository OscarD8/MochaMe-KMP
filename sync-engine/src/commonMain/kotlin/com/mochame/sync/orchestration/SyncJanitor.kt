package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppScope
import com.mochame.annotations.IoContext
import com.mochame.annotations.JanitorMutex
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.api.exceptions.toMochaException
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.infrastructure.BlobStore
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.domain.usecase.PruneIntentsUseCase
import com.mochame.sync.spi.boot.BootStatusUpdater
import com.mochame.sync.spi.policy.ExecutionPolicy
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.domain.config.JanitorMaintenanceConfig
import com.mochame.utils.interfaces.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource


/**
 * Orchestrator of state validity across different application
 * domains, all required for synchronization logic and metadata integrity.
 * Responsabilities cover recovery, initialization requirements,
 * the [SyncStatus] of intents and record pruning, and communicating system
 * stability with relevant components.
 * * [BootState]
 * * [SyncIntent]
 * * [NodeContext]
 * * [HLC]
 */
@Single(createdAtStart = true)
internal class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val transactor: TransactionProvider,
    private val pruneUseCase: PruneIntentsUseCase,
    private val hlcFactory: HlcFactory,
    private val executor: ExecutionPolicy,
    private val blobStore: BlobStore,
    private val nodeManager: NodeContextManager,
    private val intentStore: SyncIntentMaintenanceStore,
    private val config: JanitorMaintenanceConfig,
    private val timeProvider: TimeProvider,
    @IoContext private val ioContext: CoroutineContext,
    @AppScope private val appScope: CoroutineScope,
    @JanitorMutex private val mutex: Mutex,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.SYNC,
        className = "DrJntr"
    )

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks(): Job = appScope.launch(ioContext) {
        try {
            withTimeout(config.startupTimeout) {
                executor.execute("[Startup Checks]") {
                    mutex.withLock {
                        if (!isValidBootState()) {
                            logger.d { "Janitor: Skipping startup. State invalid." }
                            return@withLock
                        }

                        logger.i { "Initiating boot sequence..." }
                        bootUpdater.updateBootState(BootState.Initializing)

                        metadataMaintenance()
                        initHydration()
                        blobReconciliation()

                        bootUpdater.updateBootState(BootState.Ready)
                        logger.i { "Janitor Start Up checks finalized." }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            handleBootFailure(MochaException.Transient.BootTimeout(cause = e))
        } catch (e: Exception) {
            handleBootFailure(e.toMochaException(e.message))
        }
    }

    private fun isValidBootState(): Boolean {
        val currentState = bootUpdater.bootState.value

        return currentState is BootState.Idle
    }

    private suspend fun initHydration() = withTimeout(5.seconds) {
        val nodeContext = nodeManager.getOrEstablishContext()

        logger.i { "Hydrating HLC Factory | Last Known Local HLC: ${nodeContext.maxHlc ?: "NONE"} | NodeID: ${nodeContext.nodeId}" }

        hlcFactory.hydrate(nodeContext.maxHlc, nodeContext.nodeId)

    }

    private suspend fun metadataMaintenance() = withContext(NonCancellable) {
        val mark = TimeSource.Monotonic.markNow()

        transactor.runImmediateTransaction {
            intentStore.clearAllLocksAndResetToPending().takeIf { it > 0 }?.let {
                logger.w { "Cleared $it stale intents on boot." }
            }
        }

        logger.d { "Boot metadata maintenance complete".withTimer(mark) }
    }

    fun startRuntimeMaintenance(): Job {
        return appScope.launch(ioContext) {
            while (isActive) {
                delay(config.maintenanceInterval)
                mutex.withLock {
                    val mark = TimeSource.Monotonic.markNow()
                    logger.v { "Runtime maintenance cycle starting..." }

                    try {
                        assessStaleLeases()
                    } catch (e: Exception) {
                        logger.e(e) { "Stale lease assessment encountered error: ${e.message}" }
                    }

                    try {
                        pruneIntents()
                    } catch (e: Exception) {
                        logger.e(e) { "Intent pruning encountered error: ${e.message}" }
                    }

                    logger.v { "Runtime maintenance cycle finished".withTimer(mark) }
                }
            }
        }
    }

    /**
     * Prunes in chunks then yields, based off the limit defaulting to
     * [PruneIntentsUseCase.Companion.DEFAULT_LIMIT] and the cutoff period of
     * [PruneIntentsUseCase.Companion.DEFAULT_PRUNE_DAYS].
     */
    private suspend fun pruneIntents() {
        pruneUseCase()
    }

    /**
     * Compares blobs successfully staged in the file system (but have not shifted to
     * committed) against a local metadata record, to confirm if a crash came after the
     * database commit, meaning a retry is possible.
     * If there was a crash prior to the database commit,
     */
    private suspend fun blobReconciliation() = withContext(ioContext) {
        val mark = TimeSource.Monotonic.markNow()

        val pendingHashes = try {
            blobStore.listPendingHashes()
        } catch (e: Exception) {
            logger.e(e) { "Unable to locate pending hashes." }
            emptyList()
        }

        pendingHashes.forEach { hash ->
            try {
                if (intentStore.existsForBlob(hash)) {
                    logger.i { "Recovering stranded blob $hash. Finalizing commit." }
                    blobStore.commit(hash)
                } else {
                    logger.w { "Found orphaned pending blob $hash with no metadata. Purging." }
                    blobStore.abort(hash)
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to reconcile individual blob: $hash" }
            }
        }

        yield()

        try {
            blobStore.clearIncompleteStaging()
        } catch (e: Exception) {
            logger.w(e) { "Purging incomplete staged files terminated: ${e.message}" }
        }

        logger.i { "Blob Reconciliation Complete".withTimer(mark) }
    }

    /**
     * Janitor owns the retry lifecycle of payloads. It is the only component that sees
     * the full history of an intent across multiple sync attempts.
     */
    private suspend fun assessStaleLeases() {
        val cutoff = timeProvider.getMillisAgo(config.staleThreshold)

        transactor.runImmediateTransaction {
            val staleLeases = intentStore.getStaleLeasedIntents(cutoff)

            staleLeases.forEach { intent ->
                val newRetryCount = intent.retryCount + 1

                if (newRetryCount >= config.retryThreshold) {
                    intentStore.quarantine(
                        hlc = intent.hlc,
                        retryCount = newRetryCount
                    )
                    logger.w { "Quarantined Intent [HLC: ${intent.hlc}] [Key: ${intent.candidateKey}]" }
                } else {
                    intentStore.resetLease(
                        hlc = intent.hlc,
                        retryCount = newRetryCount
                    )
                    logger.i { "Reset Intent [HLC: ${intent.hlc}] [Key: ${intent.candidateKey}] [Retries: ${intent.retryCount}]" }
                }
            }
        }
    }


    // ----- EXCEPTION HELPERS -----
    private fun handleBootFailure(error: MochaException): MochaException {
        val failureState = error.toBootState()
        bootUpdater.updateBootState(failureState)

        if (failureState is BootState.CriticalFailure) {
            logger.e(error) { "Critical boot failure: ${error.message}" }
        } else {
            logger.w(error) { "Transient boot failure: ${error.message}" }
        }

        return error
    }

    private fun MochaException.toBootState(): BootState = when (this) {
        is MochaException.Transient -> BootState.TransientFailure(this.message, this)
        else -> BootState.CriticalFailure(this.message, this)
    }

}