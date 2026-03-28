package com.mochame.app.orchestration.sync

import co.touchlab.kermit.Logger
import com.mochame.app.data.local.room.utils.runWithRetry
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.sqlite.ExecutionPolicy
import com.mochame.app.domain.sync.MetadataStoreMaintenance
import com.mochame.app.domain.sync.MutationLedgerMaintenance
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.usecase.PruneOldEntriesUseCase
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.sync.HydrationResult
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.utils.withTimer
import com.mochame.app.infrastructure.utils.withTryLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

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
    private val executor: ExecutionPolicy,
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
            try {
                mutex.withTryLock {
                    if (!isValidBootState()) return@launch

                    logger.i { "Initiating boot sequence..." }
                    bootUpdater.updateBootState(BootState.Initializing)

                    metadataMaintenance()

                    val hydrationResult = initHydration()

                    resolveJanitorFinalState(hydrationResult)
                } ?: logger.w { "Locked out." }

            } catch (e: Exception) {
                logger.e(e) { "Critical failure during startup. $e" }
                bootUpdater.updateBootState(
                    BootState.CriticalFailure(
                        "Boot failed: ${e.message}",
                        e
                    )
                )
            }
        }
    }

    private fun isValidBootState(): Boolean {
        val currentState = bootUpdater.bootState.value

        if (currentState is BootState.Idle) return true

        logger.i { "Skipping startup. Invalid boot state of $currentState." }
        return false
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
    private suspend fun metadataMaintenance() = withContext(NonCancellable) {
        val mark = TimeSource.Monotonic.markNow()

        executor.execute() {
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