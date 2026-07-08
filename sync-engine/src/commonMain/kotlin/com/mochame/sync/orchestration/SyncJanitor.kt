package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.boot.BootState
import com.mochame.contract.boot.BootStatusUpdater
import com.mochame.contract.di.AppScope
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.JanitorMutex
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.exceptions.toMochaException
import com.mochame.contract.policy.ExecutionPolicy
import com.mochame.contract.providers.TransactionProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.sync.contract.stores.BlobStager
import com.mochame.sync.contract.HlcFactory
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.domain.stores.SyncModuleStateMaintenanceStore
import com.mochame.sync.domain.usecase.PruneOldEntriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Single(createdAtStart = true)
internal class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val transactor: TransactionProvider,
    private val moduleStore: SyncModuleStateMaintenanceStore,
    private val intentStore: SyncIntentMaintenanceStore,
    private val pruneUseCase: PruneOldEntriesUseCase,
    private val nodeManager: SyncUserProvider,
    private val hlcFactory: HlcFactory,
    private val executor: ExecutionPolicy,
    private val blobStager: BlobStager,
    @IoContext private val ioContext: CoroutineContext,
    @AppScope private val appScope: CoroutineScope,
    @JanitorMutex private val mutex: Mutex,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "DrJntr"
    )

    companion object {
        const val LEASE_TIMEOUT_MS = 30_000L
        const val RETRY_THRESHOLD = 5
    }

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        appScope.launch(ioContext) {
            try {
                withTimeout(10_000L.milliseconds) {
                    executor.execute("[Startup Checks]") {
                        mutex.withLock {
                            bootUpdater.bootState.takeIf { !isValidBootState() }?.run {
                                logger.d { "Janitor: Skipping startup. Current state ($this) is not valid for boot." }
                                return@withLock
                            }

                            logger.i { "Initiating boot sequence..." }
                            bootUpdater.updateBootState(BootState.Initializing)

                            metadataMaintenance()

                            initHydration()

                            blobReconciliation()

                            logger.i { "Janitor start up checks finalized..." }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                handleBootFailure(MochaException.Persistent.BootLockout(cause = e))

            } catch (e: Exception) {
                if (e is MochaException.Transient.BootTimeout) return@launch
                handleBootFailure(e.toMochaException(e.message))
            }
        }
    }

    private fun isValidBootState(): Boolean {
        val currentState = bootUpdater.bootState.value

        if (currentState is BootState.Idle) return true

        logger.i { "Skipping startup. Invalid boot state of $currentState." }
        return false
    }

    private suspend fun initHydration() = withTimeout(5000L) {
        try {
            val lastHlc = moduleStore.getGlobalMaxHlc()
            val nodeId = nodeManager.getOrCreateNodeId()

            logger.i { "Hydrating HLC Factory | Last Known Local HLC: ${lastHlc ?: "NONE"} | NodeID: $nodeId" }

            hlcFactory.hydrate(lastHlc, nodeId)
        } catch (e: TimeoutCancellationException) {
            handleBootFailure(
                MochaException.Transient.BootTimeout("Hydration timed out.", e)
            ).also { throw it }
        }
    }

    /**
     * Recovery Protocol.
     */
    private suspend fun metadataMaintenance() = withContext(NonCancellable) {
        val mark = TimeSource.Monotonic.markNow()

        transactor.runImmediateTransaction {
            moduleStore.ensureSeeded().takeIf { it > 0 }?.let {
                logger.i { "Maintenance: Seeded $it missing metadata entries." }
            }

            intentStore.clearAllLocksAndResetToPending().takeIf { it > 0 }?.let {
                logger.w { "Maintenance: Cleared $it stale mutation locks." }
            }
        }

        logger.d { "Maintenance Cycle Complete".withTimer(mark) }
    }

    fun startRuntimeMaintenance() {
        appScope.launch(ioContext) {
            while (true) {
                delay(LEASE_TIMEOUT_MS)
                mutex.withLock {
                    assessStaleLeases()
                    pruneInChunks()
                }
            }
        }
    }

    /**
     * Prunes in chunks then yields, based off the limit defined
     * as [PruneOldEntriesUseCase.Companion.LIMIT] and the cutoff period of
     * [PruneOldEntriesUseCase.Companion.DEFAULT_PRUNE_DAYS].
     */
    private suspend fun pruneInChunks() {
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

        // Try and restore
        val pendingHashes = blobStager.listPendingHashes()

        pendingHashes.forEach { hash ->
            if (intentStore.existsForBlob(hash)) {
                logger.i { "Maintenance: Recovering stranded blob $hash. Finalizing commit." }
                blobStager.commit(hash)
            } else {
                logger.w { "Maintenance: Found orphaned pending blob $hash with no metadata. Purging." }
                blobStager.abort(hash)
            }
        }

        yield()
        blobStager.clearIncompleteStaging()

        logger.d { "Blob Reconciliation Complete".withTimer(mark) }
    }

    /**
     * Janitor owns the retry lifecycle of payloads. It is the only component that sees
     * the full history of an intent across multiple sync attempts.
     * This runtime method runs periodically during active operation, not just on startup.
     */
    private suspend fun assessStaleLeases() {
        val cutoff = Clock.System.now().toEpochMilliseconds() - LEASE_TIMEOUT_MS

        transactor.runImmediateTransaction {
            val staleLeases = intentStore.getStaleLeasedIntents(olderThan = cutoff)

            staleLeases.forEach { intent ->
                val newRetryCount = intent.retryCount + 1

                if (newRetryCount >= RETRY_THRESHOLD) {
                    intentStore.quarantine(
                        hlc = intent.hlc,
                        retryCount = newRetryCount
                    )
                } else {
                    intentStore.resetLease(
                        hlc = intent.hlc,
                        retryCount = newRetryCount
                    )
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