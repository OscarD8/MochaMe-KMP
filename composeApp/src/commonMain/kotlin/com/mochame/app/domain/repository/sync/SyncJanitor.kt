package com.mochame.app.domain.repository.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.HydrationResult
import com.mochame.app.core.MochaModule
import com.mochame.app.core.SyncStatus
import com.mochame.app.core.appendTag
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.database.entity.SyncMetadataEntity
import com.mochame.app.di.DispatcherProvider
import com.mochame.app.domain.system.BootState
import com.mochame.app.domain.system.BootStatusUpdater
import com.mochame.app.domain.system.IdentityManager
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
    private val logger = logger.appendTag("Janitor")

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        if (bootUpdater.bootState.value !is BootState.Idle) return
        bootUpdater.updateBootState(BootState.Initializing)

        appScope.launch(dispatcherProvider.io) {
            val setupResult = runCatching {
                logger.i { "Janitor: Beginning startup sequence..." }
                performCleanBoot()

                logger.d { "Janitor: Stage 2 - Checking Metadata..." }
                ensureMetaDataIsSeeded().getOrThrow()
                val lastKnownHlc = metadataDao.getGlobalMaxHlc()
                val nodeId = identityManager.getOrCreateNodeId()

                logger.d { "Janitor: Stage 3 - Hydrating HLC..." }
                hlcFactory.hydrate(lastKnownHlc, nodeId)
            }

            val finalResult: HydrationResult = setupResult.getOrElse { e ->
                logger.e(e) { "Janitor: Critical unhandled failure during boot." }

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
                    logger.i { "Janitor: Recovered $recoveredCount modules from dirty states." }
                }
            }
        }
    }

    private fun resolveJanitorFinalState(result: HydrationResult) {
        when (result) {
            is HydrationResult.Success -> {
                logger.i { "Janitor start up checks: success." }
                bootUpdater.updateBootState(BootState.Ready)
            }
            is HydrationResult.InvalidData -> {
                val exception = result.error
                logger.e(exception) { "Janitor: Boot sequence failed." }

                val displayMessage = exception.message ?: "An unhandled ${exception::class.simpleName} occurred."
                bootUpdater.updateBootState(
                    BootState.CriticalFailure(displayMessage, exception)
                )
            }
            is HydrationResult.ClockSkewDetected -> {
                logger.e { result.error.message!! }
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
     * update at the SQL level is optimal in the [LocalFirstRepository.mutate] method.
     */
    private suspend fun ensureMetaDataIsSeeded(): Result<Unit> {
        return runCatching {
            logger.d { "Checking metadata sync status..." }

            val existingCount = metadataDao.getMetadataCount()
            val expectedCount = MochaModule.entries.size

            if (existingCount < expectedCount) {
                logger.i { "Seeding required: $existingCount/$expectedCount entries found." }

                val seeds = MochaModule.entries.map { module ->
                    SyncMetadataEntity(
                        moduleName = module,
                        syncStatus = SyncStatus.IDLE
                    )
                }

                metadataDao.seedDefaultMetadata(seeds)

                logger.i { "Successfully seeded $expectedCount metadata entries." }
            } else {
                logger.d { "Metadata is up to date." }
            }
        }.onFailure { e ->
            logger.e(e) { "Metadata seeding failed. $e." }
        }
    }
}