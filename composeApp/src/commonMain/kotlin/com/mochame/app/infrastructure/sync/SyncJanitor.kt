package com.mochame.app.infrastructure.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SyncJanitor(
    private val bootUpdater: BootStatusUpdater,
    private val database: MochaDatabase,
    private val metadataDao: SyncMetadataDao,
    private val ledgerDao: MutationLedgerDao,
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

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        if (bootUpdater.bootState.value !is BootState.Idle) return
        bootUpdater.updateBootState(BootState.Initializing)

        appScope.launch(dispatcherProvider.io) {
            val setupResult = runCatching {
                this@SyncJanitor.logger.i { "Janitor: Beginning startup sequence..." }
                performCleanBoot()

                this@SyncJanitor.logger.d { "Janitor: Stage 2 - Checking Metadata..." }
                ensureMetaDataIsSeeded().getOrThrow()
                val lastKnownHlc = metadataDao.getGlobalMaxHlc()
                val nodeId = identityManager.getOrCreateNodeId()

                this@SyncJanitor.logger.d { "Janitor: Stage 3 - Hydrating HLC..." }
                hlcFactory.hydrate(lastKnownHlc, nodeId)
            }

            val finalResult: HydrationResult = setupResult.getOrElse { e ->
                this@SyncJanitor.logger.e(e) { "Janitor: Critical unhandled failure during boot." }

                HydrationResult.InvalidData(e)
            }

            resolveJanitorFinalState(finalResult)
        }
    }

    /**
     * Recovery Protocol.
     */
    suspend fun performCleanBoot() {
        database.useWriterConnection { connection ->
            connection.withTransaction(type = Transactor.SQLiteTransactionType.IMMEDIATE) {

                ledgerDao.clearAllLocksForNonIdleModules()

                val recoveredCount = metadataDao.bulkResetDirtyModules()

                if (recoveredCount > 0) {
                    this@SyncJanitor.logger.i { "Janitor: Recovered $recoveredCount modules from dirty states." }
                }
            }
        }
    }

    private fun resolveJanitorFinalState(result: HydrationResult) {
        when (result) {
            is HydrationResult.Success -> {
                this@SyncJanitor.logger.i { "Janitor start up checks: success." }
                bootUpdater.updateBootState(BootState.Ready)
            }
            is HydrationResult.InvalidData -> {
                val exception = result.error
                this@SyncJanitor.logger.e(exception) { "Janitor: Boot sequence failed." }

                val displayMessage = exception.message ?: "An unhandled ${exception::class.simpleName} occurred."
                bootUpdater.updateBootState(
                    BootState.CriticalFailure(displayMessage, exception)
                )
            }
            is HydrationResult.ClockSkewDetected -> {
                this@SyncJanitor.logger.e { result.error.message!! }
                BootState.CriticalFailure( result.error.message!!, result.error )
            }
        }
    }

    /**
     * Looks to all Modules and establishes if a metadata row exists. Insert
     * with onConflict onReplace ensures no overwriting of data.
     *
     * This is currently required in order to implement simple update statements
     * for the metadata tables, as apparently as this is a frequent operation,
     * update at the SQL level is optimal in the [com.mochame.app.data.common.LocalFirstRepository.mutate] method.
     */
    private suspend fun ensureMetaDataIsSeeded(): Result<Unit> {
        return runCatching {
            this.logger.d { "Checking metadata sync status..." }

            val existingCount = metadataDao.getMetadataCount()
            val expectedCount = MochaModule.entries.size

            if (existingCount < expectedCount) {
                this.logger.i { "Seeding required: $existingCount/$expectedCount entries found." }

                val seeds = MochaModule.entries.map { module ->
                    SyncMetadataEntity(
                        moduleName = module,
                        syncStatus = SyncStatus.IDLE
                    )
                }

                metadataDao.seedDefaultMetadata(seeds)

                this.logger.i { "Successfully seeded $expectedCount metadata entries." }
            } else {
                this.logger.d { "Metadata is up to date." }
            }
        }.onFailure { e ->
            this@SyncJanitor.logger.e(e) { "Metadata seeding failed. $e." }
        }
    }
}