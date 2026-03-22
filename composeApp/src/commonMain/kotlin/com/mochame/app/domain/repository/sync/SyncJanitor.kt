package com.mochame.app.domain.repository.sync

import androidx.room.Transactor
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.HydrationResult
import com.mochame.app.core.MochaModule
import com.mochame.app.core.SyncStatus
import com.mochame.app.core.SystemStatus
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.database.entity.SyncMetadataEntity
import com.mochame.app.di.DispatcherProvider
import com.mochame.app.domain.system.IdentityManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class SyncJanitor(
    private val database: MochaDatabase,
    private val metadataDao: SyncMetadataDao,
    private val ledgerDao: MutationLedgerDao,
    private val identityManager: IdentityManager,
    private val dispatcherProvider: DispatcherProvider,
    private val appScope: CoroutineScope,
    private val hlcFactory: HlcFactory,
    private val logger: Logger
) {

    private val _systemStatus = MutableStateFlow<SystemStatus>(SystemStatus.Initializing)
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _isInitialized = CompletableDeferred<Unit>()
    val isInitialized: Deferred<Unit> = _isInitialized

    init {
        startupChecks()
    }

    /**
     * The single entry point for app initialization.
     */
    fun startupChecks() {
        appScope.launch(dispatcherProvider.io) {
            try {
                // 1. IMMEDIATE
                performCleanBoot()

                // 2. PLACEHOLDERS:
                ensureMetaDataIsSeeded()

                // 3. DATA:
                val lastKnownHlc = metadataDao.getGlobalMaxHlc()
                val nodeId = identityManager.getOrCreateNodeId()

                // 4. HLC:
                val result = hlcFactory.hydrate(lastKnownHlc.toString(), nodeId)

                // 5. UI STATUS:
                handleHydrationResult(result)

                if(result is HydrationResult.Success || result is HydrationResult.NewInstall) {
                    _isInitialized.complete(Unit)
                    logger.i { "Janitor signaled BaseRepository ." }
                }
            } catch (e: Exception) {
                logger.e(e) { "Critical Startup Failure. BaseRepository will be blocked" }
                _systemStatus.value = SystemStatus.Error("Database Repair Failed")
                _isInitialized.completeExceptionally(e)
            }
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

    private fun handleHydrationResult(result: HydrationResult) {
        logger.logHydration(result)

        _systemStatus.value = when (result) {
            is HydrationResult.Success -> {
                SystemStatus.Ready
            }
            is HydrationResult.NewInstall -> {
                SystemStatus.Ready
            }
            is HydrationResult.InvalidData -> {
                SystemStatus.Error("Time Integrity Failure: Local data is unreadable.")
            }
            is HydrationResult.ClockSkewDetected -> {
                SystemStatus.Error("System Clock Error: Please sync your device time.")
            }
        }
    }

    private fun Logger.logHydration(result: HydrationResult) {
        when (result) {
            is HydrationResult.Success -> d { "Success: ${result.hlc}" }
            is HydrationResult.NewInstall -> i { "New Install: ${result.hlc}" }
            is HydrationResult.InvalidData -> e(result.error) { "Invalid Data" }
            is HydrationResult.ClockSkewDetected -> w { "Clock Skew: ${result.driftMs}ms" }
        }
    }

    /**
     * Looks to all Modules and establishes if a metadata row exists. Insert
     * with onConflict onReplace ensures no overwriting of data.
     *
     * This is currently required in order to implement simple update statements
     * for the metadata tables, as apparently as this is a frequent operation,
     * update at the SQL level is optimal in the [BaseRepository.mutate] method.
     */
    private suspend fun ensureMetaDataIsSeeded() {
       // 1. Perform O(1) count check
        val existingCount = metadataDao.getMetadataCount()
        val expectedCount = MochaModule.entries.size

        // 2. Only trigger the O(N) mapping and insertion if there's a discrepancy
        if (existingCount < expectedCount) {
            val seeds = MochaModule.entries.map { module ->
                SyncMetadataEntity(
                    moduleName = module,
                    syncStatus = SyncStatus.IDLE
                )
            }
            metadataDao.seedDefaultMetadata(seeds)
        }
    }
}