package com.mochame.sync.infrastructure.stores


import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.data.daos.SyncIntentDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.domain.stores.SyncIntentStore
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import org.koin.core.annotation.Single

@Single(binds = [SyncIntentStore::class, SyncIntentMaintenanceStore::class])
class DefaultSyncIntentStore(
    private val dao: SyncIntentDao
) : SyncIntentStore, SyncIntentMaintenanceStore {

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

    override suspend fun existsForBlob(blobId: String) =
        dao.existsByBlobId(blobId)

    /**
     * Assumes use in a stale context. No sync should be active, as it will
     * reset all syncIds to null and the status to Pending.
     * At the ledger level, this would reflect that single entries require a
     * further attempt to sync.
     * Should be used in conjunction with [com.mochame.app.domain.sync.stores.MetadataStoreMaintenance.bulkResetDirtyModules].
     *
     */
    override suspend fun clearAllLocksAndResetToPending(): Int {
        return dao.clearAllLocksAndResetStatus()
    }

    override suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int {
        return dao.pruneOldSynced(cutoff = olderThan, limit = limit)
    }

}