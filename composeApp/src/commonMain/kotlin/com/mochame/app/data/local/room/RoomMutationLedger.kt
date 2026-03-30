package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.entity.MutationEntryEntity
import com.mochame.app.domain.system.sync.MutationLedger
import com.mochame.app.domain.system.sync.MutationLedgerMaintenance

class RoomMutationLedger(private val dao: MutationLedgerDao)
    : MutationLedger, MutationLedgerMaintenance
{
    override suspend fun getPending(
        candidateKey: String,
        entityType: String
    ): MutationEntryEntity? {
        return dao.getPendingMutation(candidateKey, entityType)
    }

    override suspend fun recordIntent(entry: MutationEntryEntity) = dao.upsert(entry)

    override suspend fun discardIntent(hlc: String) {
        return dao.deleteByHlc(hlc)
    }

    override suspend fun clearAllLocksAndResetToPending(): Int {
        return dao.clearAllLocksAndResetStatus()
    }

    override suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int {
        return dao.pruneOldSynced(cutoff = olderThan, limit = limit)
    }


}