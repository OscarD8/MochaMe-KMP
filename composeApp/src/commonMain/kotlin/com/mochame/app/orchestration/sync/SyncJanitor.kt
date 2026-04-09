package com.mochame.app.orchestration.sync

import co.touchlab.kermit.Logger
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.domain.sync.stores.MetadataStoreMaintenance
import com.mochame.app.domain.sync.stores.MutationLedgerMaintenance
import com.mochame.app.domain.sync.usecase.PruneOldEntriesUseCase
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.logging.withTimer
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.utils.toMochaException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.time.TimeSource

class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val transactor: TransactionProvider,
    private val metadataStore: MetadataStoreMaintenance,
    private val ledgerStore: MutationLedgerMaintenance,
    private val pruneUseCase: PruneOldEntriesUseCase,
    private val identityManager: IdentityManager,
    private val dispatcher: DispatcherProvider,
    private val appScope: CoroutineScope,
    private val hlcFactory: HlcFactory,
    private val mutex: Mutex,
    private val executor: ExecutionPolicy,
    private val blobAdmin: BlobStager,
    logger: Logger
) {
    companion object {
        private const val TAG = "Janitor"
    }

    private val logger = logger.appendTag(TAG)

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        appScope.launch(dispatcher.io) {
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

                            logger.i { "Hydration: HLCFactory is hydrated." }
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
    private suspend fun blobReconciliation() = withContext(dispatcher.io) {
        val mark = TimeSource.Monotonic.markNow()

        // Try and restore
        val pendingHashes = blobAdmin.listPendingHashes()

        pendingHashes.forEach { hash ->
            if (ledgerStore.existsForBlob(hash)) {
                logger.i { "Maintenance: Recovering stranded blob $hash. Finalizing commit." }
                blobAdmin.commit(hash)
            } else {
                logger.w { "Maintenance: Found orphaned pending blob $hash with no metadata. Purging." }
                blobAdmin.abort(hash)
            }
        }

        yield()
        blobAdmin.clearIncompleteStaging()

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