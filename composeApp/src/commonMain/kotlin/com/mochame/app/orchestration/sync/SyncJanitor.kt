package com.mochame.app.orchestration.sync

import co.touchlab.kermit.Logger
import com.mochame.app.data.local.toMochaException
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.system.sync.MetadataStoreMaintenance
import com.mochame.app.domain.system.sync.MutationLedgerMaintenance
import com.mochame.app.domain.system.sync.TransactionProvider
import com.mochame.app.domain.system.sync.usecase.PruneOldEntriesUseCase
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.utils.withTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val mutex: Mutex,
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
        appScope.launch(dispatcherProvider.io) {
            try {
                withTimeout(15_000L) {
                    mutex.withLock {
                        bootUpdater.bootState.takeIf { !isValidBootState() }?.run {
                            logger.d { "Janitor: Skipping startup. Current state ($this) is not valid for boot." }
                            return@withLock
                        }

                        logger.i { "Initiating boot sequence..." }
                        bootUpdater.updateBootState(BootState.Initializing)

                        metadataMaintenance()

                        initHydration()

                        logger.i { "Hydration: HLCFactory is hydrated." }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                handleBootFailure(
                    MochaException.Persistent.BootTimeout("Boot sequence timed out...")
                )
            } catch (e: Exception) {
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
        val lastHlc = metadataStore.getGlobalMaxHlc()
        val nodeId = identityManager.getOrCreateNodeId()

        logger.i { "Hydrating HLC Factory | Last Known Local HLC: ${lastHlc ?: "NONE"} | NodeID: $nodeId" }

        hlcFactory.hydrate(lastHlc, nodeId)
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

    // ----- EXCEPTION HELPERS -----
    private fun handleBootFailure(error: MochaException) {
        val failureState = error.toBootState()
        bootUpdater.updateBootState(failureState)

        if (failureState is BootState.CriticalFailure) {
            logger.e(error) { "Critical boot failure: ${error.message}" }
        } else {
            logger.w(error) { "Transient boot failure: ${error.message}" }
        }
    }

    private fun MochaException.toBootState(): BootState = when (this) {
        is MochaException.Transient -> BootState.TransientFailure(this.message, this)
        else -> BootState.CriticalFailure(this.message, this)
    }

}