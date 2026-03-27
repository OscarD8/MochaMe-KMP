package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.Logger
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.sync.MetadataStoreMaintenance
import com.mochame.app.domain.sync.MutationLedgerMaintenance
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.PruneOldEntriesUseCase
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import com.mochame.app.data.common.LocalFirstRepository


class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val transactor: TransactionProvider,
    private val metadataStore: MetadataStoreMaintenance,
    private val ledgerStore: MutationLedgerMaintenance,
    private val pruneUseCase: PruneOldEntriesUseCase,
    private val identityManager: IdentityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val appScope: CoroutineScope,
    private val hlcFactory: HlcFactory,
    logger: Logger
) {
    companion object {
        private const val TAG = "Janitor"
    }

    private val logger = logger.appendTag(TAG)
    private val mutex = Mutex()

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        appScope.launch(dispatcherProvider.io) {
            if (!isValidStartUpState()) return@launch

            try {
                logger.i { "Janitor: Initiating boot sequence..." }
                bootUpdater.updateBootState(BootState.Initializing)

                val hydrationResult = initHydration()

                withContext(NonCancellable) {
                    metadataMaintenance()
                }

                resolveJanitorFinalState(hydrationResult)

            } catch (e: Exception) {
                logger.e(e) { "Janitor: Critical Failure during startup" }
                bootUpdater.updateBootState(
                    BootState.CriticalFailure(
                        "Boot failed: ${e.message}",
                        e
                    )
                )
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun isValidStartUpState(): Boolean {
        if (!mutex.tryLock()) {
            logger.w { "Aborting startupChecks: Janitor is already running (Mutex locked)." }
            return false
        }

        val currentState = bootUpdater.bootState.value
        if (currentState !is BootState.Idle) {
            logger.i { "Janitor: Skipping startup. Current state is $currentState" }
            return false
        }

        return true
    }

    private suspend fun initHydration(): HydrationResult {
        val lastHlc = metadataStore.getGlobalMaxHlc()
        val nodeId = identityManager.getOrCreateNodeId()

        logger.i { "Hydrating HLC Factory | Last Known HLC: ${lastHlc ?: "NONE"} | NodeID: $nodeId" }

        return hlcFactory.hydrate(lastHlc, nodeId)
    }

    /**
     * Recovery Protocol.
     */
    private suspend fun metadataMaintenance() {
        val startTime = System.currentTimeMillis()

        transactor.runImmediateTransaction {
            ensureMetaDataIsSeeded()

            ledgerStore.clearAllLocksAndResetToPending().takeIf { it > 0 }
                ?.let { logger.w { "Maintenance: Cleared $it stale mutation locks." } }

            val recoveredModules = metadataStore.getDirtyModuleNames()
            if (recoveredModules.isNotEmpty()) {
                logger.i { "Maintenance: Recovered dirty modules: ${recoveredModules.joinToString()}" }
                metadataStore.bulkResetDirtyModules()
            }

            pruneUseCase().takeIf { it > 0 }
                ?.let { logger.d { "Maintenance: Pruned $it tombstone entries." } }
        }

        logger.d { "Maintenance Cycle Complete. Duration: ${System.currentTimeMillis() - startTime}ms" }
    }


    /**
     * Looks to all Modules and establishes if a metadata row exists. Insert
     * with onConflict onReplace ensures no overwriting of data.
     *
     * This is currently required in order to implement simple update statements
     * for the metadata tables, as apparently as this is a frequent operation,
     * update at the SQL level is optimal in the [LocalFirstRepository] method.
     */
    private suspend fun ensureMetaDataIsSeeded() {
        val existingCount = metadataStore.getMetadataCount()
        val expectedCount = MochaModule.entries.size

        if (existingCount < expectedCount) {
            logger.i { "Seeding required: $existingCount/$expectedCount entries found." }

            val seeds = MochaModule.entries.map { module ->
                SyncMetadataEntity(
                    moduleName = module,
                    syncStatus = SyncStatus.IDLE
                )
            }

            metadataStore.seedDefaultMetadata(seeds)

            logger.i { "Successfully seeded $expectedCount metadata entries." }
        } else {
            logger.d { "Metadata is up to date." }
        }
    }

    private fun resolveJanitorFinalState(result: HydrationResult) {
        when (result) {
            is HydrationResult.Success -> {
                logger.i { "Hydration: success." }
                bootUpdater.updateBootState(BootState.Ready)
            }

            is HydrationResult.InvalidData -> {
                logger.e { result.error.message!! }
                bootUpdater.updateBootState(
                    BootState.CriticalFailure(result.error.message!!)
                )
            }

            is HydrationResult.ClockSkewDetected -> {
                logger.e { result.error.message!! }
                bootUpdater.updateBootState(
                    BootState.CriticalFailure("Clock Skew", result.error)
                )
            }
        }
    }
}