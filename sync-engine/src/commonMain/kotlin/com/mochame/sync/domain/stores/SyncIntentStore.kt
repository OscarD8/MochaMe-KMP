package com.mochame.sync.domain.stores


import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.data.entities.SyncIntentEntity
import kotlinx.coroutines.flow.Flow

interface SyncIntentStore {
    suspend fun getPendingByPrimaryKey(
        candidateKey: String,
        entityType: MochaModule
    ): SyncIntentEntity?

    suspend fun recordIntent(entry: SyncIntentEntity)
    suspend fun getPendingByModule(module: MochaModule): List<SyncIntentEntity?>
    suspend fun discardIntent(hlc: String)

    suspend fun observePendingCount() : Flow<Int>

}

interface SyncIntentMaintenanceStore : SyncIntentStore {
    suspend fun clearAllLocksAndResetToPending(): Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
}