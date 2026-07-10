package com.mochame.sync.domain.stores


import com.mochame.sync.contract.stores.SyncIntentStore
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.model.QuarantinedModuleSummary
import com.mochame.sync.orchestration.SyncCoordinator
import kotlinx.coroutines.flow.Flow

internal interface SyncIntentMaintenanceStore {
    suspend fun clearAllLocksAndResetToPending(): Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
    suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntent>
    suspend fun quarantine(hlc: HLC, retryCount: Int)

    /**
     * Sets status to pending, applying the retry attempts whilst nullifying the syncId,
     * freeing the entity for a new batch, applied by the [SyncCoordinator].
     */
    suspend fun resetLease(hlc: HLC, retryCount: Int)
    suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedModuleSummary>>
}