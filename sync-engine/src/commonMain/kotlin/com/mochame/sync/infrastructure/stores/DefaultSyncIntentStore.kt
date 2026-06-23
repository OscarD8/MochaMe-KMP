package com.mochame.sync.infrastructure.stores


import com.mochame.sync.contract.models.HLC
import com.mochame.sync.data.daos.SyncIntentDao
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.domain.model.QuarantinedModuleSummary
import com.mochame.sync.contract.SyncStatus
import com.mochame.sync.domain.stores.SyncIntentStore
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single(binds = [SyncIntentStore::class, SyncIntentMaintenanceStore::class])
class DefaultSyncIntentStore(
    private val dao: SyncIntentDao
) : SyncIntentStore, SyncIntentMaintenanceStore {

    override suspend fun getPendingByPrimaryKey(
        candidateKey: String,
        module: String
    ): SyncIntentEntity? {
        return dao.getPendingByKey(candidateKey, module)
    }

    override suspend fun getPendingByModule(module: String): List<SyncIntentEntity?> {
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
     * At the level of individual intents, this would reflect that single entries require a
     * further attempt to sync.
     */
    override suspend fun clearAllLocksAndResetToPending(): Int {
        return dao.clearAllLocksAndResetStatus()
    }

    override suspend fun pruneOldSynced(olderThan: Long, limit: Int): Int {
        return dao.pruneOldSynced(cutoff = olderThan, limit = limit)
    }

    override suspend fun observePendingCount(): Flow<Int> {
        return dao.observePendingCount()
    }

    override suspend fun stampLastError(hlcs: List<String>, message: String) {
        if(hlcs.isNotEmpty()) dao.stampLastError(hlcs, message)
    }

    override suspend fun claimBatch(sessionId: String, limit: Int): Int =
        dao.claimBatch(sessionId, limit)

    override suspend fun getClaimedBatch(sessionId: String): List<SyncIntentEntity> =
        dao.getClaimedBatch(sessionId)

    override suspend fun acknowledgeSuccess(hlcList: List<String>) {
        hlcList.forEach { hlc ->
            dao.setStatus(HLC.parse(hlc), SyncStatus.SUCCESS)
        }
    }

    override suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntentEntity> =
        dao.getStaleLeasedIntents(olderThan)

    override suspend fun resetLease(hlc: String, retryCount: Int) =
        dao.resetLease(hlc, retryCount)

    override suspend fun quarantine(
        hlc: String,
        retryCount: Int
    ) = dao.quarantineIntent(hlc, retryCount)

    override suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedModuleSummary>> =
        dao.observeQuarantinedCountByModule()

}