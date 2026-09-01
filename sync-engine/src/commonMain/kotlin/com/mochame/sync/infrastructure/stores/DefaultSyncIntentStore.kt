package com.mochame.sync.infrastructure.stores


import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.data.SyncIntentDao
import com.mochame.sync.data.toDomain
import com.mochame.sync.data.toEntity
import com.mochame.sync.spi.models.QuarantinedFeatureSummary
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.models.SyncIntent
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * An implementation coupled to Jetpack Room, bridging the orchestration layer of synchronization logic
 * to the database infrastructure. This store covers the split domains - record maintenance and
 * direct recording logic.
 *
 * The store handles the model transitions from domain to data, expecting to receive domain
 * models and pass back domain models. Verifying the integrity of this component means
 * asserting the parity of that mapping logic, providing a seamless bridge between orchestration
 * and data persistence.
 */
@Single(binds = [SyncIntentStore::class, SyncIntentMaintenanceStore::class])
internal class DefaultSyncIntentStore(
    @Provided private val dao: SyncIntentDao
) : SyncIntentStore, SyncIntentMaintenanceStore {

    override suspend fun getPendingByCandidateKey(candidateKey: Long) =
        dao.getPendingByKey(candidateKey)?.toDomain()

    override suspend fun getPendingByFeature(feature: FeatureContext): List<SyncIntent?> =
        dao.getPendingByFeature(feature.featureName).map { it.toDomain() }

    override suspend fun recordIntent(entry: SyncIntent) = dao.upsert(entry.toEntity())

    override suspend fun discardIntent(hlc: HLC) = dao.deleteByHlc(hlc.toString())

    override suspend fun stampLastError(hlcs: List<HLC>, message: String) {
        if (hlcs.isNotEmpty()) dao.stampLastError(
            hlcs.map { it.toString() },
            message
        )
    }

    override suspend fun claimBatch(batchId: String, limit: Int): Int =
        dao.claimBatch(batchId, limit)

    override suspend fun getClaimedBatch(batchId: String): List<SyncIntent> =
        dao.getClaimedBatch(batchId).map { it.toDomain() }

    override suspend fun acknowledgeSuccess(hlcList: List<HLC>) =
        dao.markAsSynced(hlcList.map { it.toString() })


    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    /**
     * Assumes use in a stale context. No sync should be active, as it will
     * reset all syncIds to null and the status to Pending.
     * At the level of individual intents, this would reflect that single entries require a
     * further attempt to sync.
     */
    override suspend fun clearAllLocksAndResetToPending(): Int =
        dao.clearAllLocksAndResetToPending()

    override suspend fun resetLease(hlc: HLC) =
        dao.resetLease(hlc.toString())

    override suspend fun releaseBatch(batchId: String) = dao.resetLease(
        syncId = batchId,
    )

    override suspend fun pruneOldSynced(pruneAfter: Long, limit: Int) =
        dao.pruneOldSynced(
            cutoffMs = pruneAfter,
            limit = limit
        )

    override suspend fun quarantine(hlc: HLC, retryCount: Int) =
        dao.quarantineIntent(hlc.toString(), retryCount)

    override suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedFeatureSummary>> =
        dao.observeQuarantinedCountByModule()

    override suspend fun getStaleLeasedIntents(olderThan: Long): List<SyncIntent> =
        dao.getStaleLeasedIntents(olderThan).map { it.toDomain() }

    override suspend fun existsForBlob(blobId: String) =
        dao.existsByBlobId(blobId)

}