package com.mochame.sync.spi.domain

import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.models.QuarantinedFeatureSummary
import com.mochame.sync.spi.models.SyncIntent
import kotlinx.coroutines.flow.Flow

interface SyncIntentMaintenanceStore {
    suspend fun clearAllLocksAndResetToPending(): Int
    suspend fun pruneOldSynced(pruneAfter: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
    suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntent>
    suspend fun quarantine(hlc: HLC, retryCount: Int)

    /**
     * Sets status to pending, applying the retry attempts whilst nullifying the syncId,
     * freeing the entity for a new batch, applied by the [com.mochame.sync.orchestration.SyncCoordinator].
     */
    suspend fun resetLease(hlc: HLC, retryCount: Int)
    suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedFeatureSummary>>
}