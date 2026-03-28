package com.mochame.app.domain.sync

import com.mochame.app.data.local.room.entity.MutationEntryEntity

interface MutationLedger {
    suspend fun getPending(candidateKey: String, entityType: String): MutationEntryEntity?
    suspend fun recordIntent(entry: MutationEntryEntity)
    suspend fun discardIntent(hlc: String)
}

interface MutationLedgerMaintenance {
    suspend fun clearAllLocksAndResetToPending() : Int
    suspend fun pruneOldSynced(olderThan: Long, limit: Int) : Int
}