package com.mochame.sync.domain.stores


import com.mochame.orchestrator.MochaModule
import com.mochame.sync.data.entities.SyncIntentEntity

interface MutationLedger {
    suspend fun getPendingByKey(
        candidateKey: String,
        entityType: MochaModule
    ): SyncIntentEntity?

    suspend fun recordIntent(entry: SyncIntentEntity)
    suspend fun discardIntent(hlc: String)
    suspend fun getPendingByModule(module: MochaModule): List<SyncIntentEntity?>
}

interface MutationLedgerMaintenance {
    suspend fun clearAllLocksAndResetToPending(): Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
}