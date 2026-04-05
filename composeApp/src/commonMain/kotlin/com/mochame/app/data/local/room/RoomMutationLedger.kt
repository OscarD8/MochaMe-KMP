package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.entity.SyncIntentEntity
import com.mochame.app.domain.sync.MetadataStoreMaintenance
import com.mochame.app.domain.sync.MutationLedger
import com.mochame.app.domain.sync.MutationLedgerMaintenance
import com.mochame.app.domain.sync.utils.MochaModule

class RoomMutationLedger(
    private val dao: MutationLedgerDao
) : MutationLedger, MutationLedgerMaintenance {

    override suspend fun getPendingByKey(
        candidateKey: String,
        entityType: MochaModule
    ): SyncIntentEntity? {
        return dao.getPendingByKey(candidateKey, entityType)
    }

    override suspend fun getPendingByModule(module: MochaModule): List<SyncIntentEntity?> {
        return dao.getPendingByModule(module)
    }

    override suspend fun recordIntent(entry: SyncIntentEntity) {
        return dao.upsert(entry)
    }

    override suspend fun discardIntent(hlc: String) {
        return dao.deleteByHlc(hlc)
    }

    /**
     * Assumes use in a stale context. No sync should be active, as it will
     * reset all syncIds to null and the status to Pending.
     * At the ledger level, this would reflect that single entries require a
     * further attempt to sync.
     * Should be used in conjunction with [MetadataStoreMaintenance.bulkResetDirtyModules].
     *
     */
    override suspend fun clearAllLocksAndResetToPending(): Int {
        return dao.clearAllLocksAndResetStatus()
    }

    override suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int {
        return dao.pruneOldSynced(cutoff = olderThan, limit = limit)
    }

}