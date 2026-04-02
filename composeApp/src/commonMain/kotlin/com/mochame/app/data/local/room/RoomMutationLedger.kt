package com.mochame.app.data.local.room

import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.entity.MutationLedgerEntity
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.domain.system.sync.MutationLedger
import com.mochame.app.domain.system.sync.MutationLedgerMaintenance
import com.mochame.app.domain.system.sync.MetadataStoreMaintenance
import com.mochame.app.domain.system.sync.utils.MochaModule

class RoomMutationLedger(
    private val dao: MutationLedgerDao,
    private val executor: ExecutionPolicy
) : MutationLedger, MutationLedgerMaintenance {

    override suspend fun getPendingByKey(
        candidateKey: String,
        entityType: MochaModule
    ): MutationLedgerEntity? = executor.execute {
        dao.getPendingByKey(candidateKey, entityType)
    }

    override suspend fun getPendingByModule(module: MochaModule) : List<MutationLedgerEntity?>
    = executor.execute {
        dao.getPendingByModule(module)
    }

    override suspend fun recordIntent(entry: MutationLedgerEntity) =
        executor.execute { dao.upsert(entry) }

    override suspend fun discardIntent(hlc: String) = executor.execute {
        dao.deleteByHlc(hlc)
    }

    /**
     * Assumes use in a stale context. No sync should be active, as it will
     * reset all syncIds to null and the status to Pending.
     * At the ledger level, this would reflect that single entries require a
     * further attempt to sync.
     * Should be used in conjunction with [MetadataStoreMaintenance.bulkResetDirtyModules].
     *
     */
    override suspend fun clearAllLocksAndResetToPending(): Int = executor.execute {
        dao.clearAllLocksAndResetStatus()
    }

    override suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int =
        executor.execute { dao.pruneOldSynced(cutoff = olderThan, limit = limit) }

}