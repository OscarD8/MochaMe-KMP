package com.mochame.sync.domain.stores


import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.domain.model.QuarantinedModuleSummary
import com.mochame.sync.orchestration.SyncCoordinator
import kotlinx.coroutines.flow.Flow

interface SyncIntentStore {
    suspend fun getPendingByPrimaryKey(
        candidateKey: String,
        module: String
    ): SyncIntentEntity?

    suspend fun recordIntent(entry: SyncIntentEntity)
    suspend fun getPendingByModule(module: String): List<SyncIntentEntity?>
    suspend fun discardIntent(hlc: String)
    suspend fun observePendingCount(): Flow<Int>
    suspend fun claimBatch(sessionId: String, limit: Int = 50): Int
    suspend fun getClaimedBatch(sessionId: String): List<SyncIntentEntity>

    suspend fun acknowledgeSuccess(hlcList: List<String>)
    suspend fun stampLastError(hlcs: List<String>, message: String)
    suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedModuleSummary>>
}

interface SyncIntentMaintenanceStore : SyncIntentStore {
    suspend fun clearAllLocksAndResetToPending(): Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
    suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntentEntity>
    suspend fun quarantine(hlc: String, retryCount: Int)

    /**
     * Sets status to pending, applying the retry attempts whilst nullifying the syncId,
     * freeing the entity for a new batch, applied by [SyncCoordinator.startOutboundPipeline].
     */
    suspend fun resetLease(hlc: String, retryCount: Int)
}