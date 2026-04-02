package com.mochame.app.domain.system.sync

import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.domain.system.sync.utils.SyncStatus
import com.mochame.app.infrastructure.sync.HLC

interface MetadataStore {
    suspend fun recordPendingMetadata(module: MochaModule, hlc: HLC)

    suspend fun updateSyncingToFailure(
        module: MochaModule,
        fromStatus: SyncStatus = SyncStatus.SYNCING,
        toStatus: SyncStatus = SyncStatus.FAILED
    )

    suspend fun updateSyncingToSuccess(
        module: MochaModule,
        fromStatus: SyncStatus = SyncStatus.SYNCING,
        toStatus: SyncStatus = SyncStatus.SUCCESS
    )

    suspend fun updatePendingToSyncing(
        module: MochaModule,
        fromStatus: SyncStatus = SyncStatus.PENDING,
        toStatus: SyncStatus = SyncStatus.SYNCING
    )

    suspend fun stampModuleMetadata(
        module: MochaModule,
        watermark: String?,
        timestamp: Long,
        status: SyncStatus = SyncStatus.PENDING
    )

    suspend fun finalizeSync(module: MochaModule, syncId: String, newWatermark: String)
}

interface MetadataStoreMaintenance {
    /**
     * Performs a sweep of all modules currently not Idle
     * and resets status to Pending (by default).
     * Used in a context where the state is stale.
     * Should be used in conjunction with [MutationLedgerMaintenance.clearAllLocksAndResetToPending].
     */
    suspend fun bulkResetDirtyModules(): Int

    suspend fun getDirtyModuleNames(): List<String>

    suspend fun getMetadataCount(): Int

    /**
     * Ensures all MochaModules have a corresponding metadata row.
     * Infrastructure handles mapping and technical checks.
     */
    suspend fun ensureSeeded(): Int

    /**
     * This will represent the max HLC of the local device.
     * In cloud integration, it should probably have some way
     * of working with the Coordinator to ensure this value
     * represents the global truth.
     */
    suspend fun getGlobalMaxHlc(): String?

}
