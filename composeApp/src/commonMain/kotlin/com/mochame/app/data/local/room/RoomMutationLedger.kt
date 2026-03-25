package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.entity.MutationEntryEntity
import com.mochame.app.domain.sync.MutationLedger

class RoomMutationLedger(private val dao: MutationLedgerDao) : MutationLedger {
    override suspend fun getPending(
        candidateKey: String,
        entityType: String
    ): MutationEntryEntity? {
        return dao.getPendingMutation(candidateKey, entityType)
    }

    override suspend fun upsert(entry: MutationEntryEntity) = dao.upsert(entry)

    override suspend fun deleteByHlc(hlc: String) {
        return dao.deleteByHlc(hlc)
    }

}