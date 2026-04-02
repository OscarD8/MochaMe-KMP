package com.mochame.app.domain.system.sync

import com.mochame.app.data.local.room.entity.MutationLedgerEntity
import com.mochame.app.domain.system.sync.utils.MochaModule

interface MutationLedger {
    suspend fun getPendingByKey(candidateKey: String, entityType: MochaModule): MutationLedgerEntity?
    suspend fun recordIntent(entry: MutationLedgerEntity)
    suspend fun discardIntent(hlc: String)
    suspend fun getPendingByModule(module: MochaModule): List<MutationLedgerEntity?>
}

interface MutationLedgerMaintenance {
    suspend fun clearAllLocksAndResetToPending() : Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int) : Int
}