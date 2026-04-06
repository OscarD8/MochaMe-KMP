package com.mochame.app.domain.sync.stores

import com.mochame.app.data.local.room.entities.SyncIntentEntity
import com.mochame.app.domain.sync.utils.MochaModule

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
}