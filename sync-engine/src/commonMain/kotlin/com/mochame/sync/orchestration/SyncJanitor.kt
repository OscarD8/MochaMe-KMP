package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.di.AppScope
import com.mochame.di.IoContext
import com.mochame.di.JanitorMutex
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.metadata.BootState
import com.mochame.metadata.BootStatusUpdater
import com.mochame.platform.policies.ExecutionPolicy
import com.mochame.platform.providers.TransactionProvider
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.sync.domain.stores.BlobStager
import com.mochame.sync.domain.stores.MetadataStoreMaintenance
import com.mochame.sync.domain.stores.MutationLedgerMaintenance
import com.mochame.sync.domain.usecase.PruneOldEntriesUseCase
import com.mochame.sync.infrastructure.HlcFactory
import com.mochame.utils.exceptions.MochaException
import com.mochame.utils.exceptions.toMochaException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.TimeSource

@Single(createdAtStart = true)
class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val transactor: TransactionProvider,
    private val metadataStore: MetadataStoreMaintenance,
    private val ledgerStore: MutationLedgerMaintenance,
    private val pruneUseCase: PruneOldEntriesUseCase,
    private val identityManager: SyncUserProvider,
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
        className = "Janitor"
    )

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        appScope.launch(ioContext) {
            try {
                withTimeout(15_000L) {
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
                handleBootFailure(e.toMochaException())
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
            val lastHlc = metadataStore.getGlobalMaxHlc()
            val nodeId = identityManager.getOrCreateNodeId()

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
            metadataStore.ensureSeeded().takeIf { it > 0 }?.let {
                logger.i { "Maintenance: Seeded $it missing metadata entries." }
            }

            ledgerStore.clearAllLocksAndResetToPending().takeIf { it > 0 }?.let {
                logger.w { "Maintenance: Cleared $it stale mutation locks." }
            }

            val recoveredModules = metadataStore.getDirtyModuleNames()
            if (recoveredModules.isNotEmpty()) {
                logger.i { "Maintenance: Recovered dirty modules: ${recoveredModules.joinToString()}" }
                metadataStore.bulkResetDirtyModules()
            }
        }

        logger.d { "Maintenance Cycle Complete".withTimer(mark) }
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
            if (ledgerStore.existsForBlob(hash)) {
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