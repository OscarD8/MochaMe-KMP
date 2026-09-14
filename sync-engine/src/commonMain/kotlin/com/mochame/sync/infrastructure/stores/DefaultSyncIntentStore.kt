package com.mochame.sync.infrastructure.stores


import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.data.SyncIntentDao
import com.mochame.sync.data.toDomain
import com.mochame.sync.data.toEntity
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.models.QuarantinedFeatureSummary
import com.mochame.sync.spi.models.SyncIntent
import kotlinx.coroutines.flow.Flow
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
    private val intentDao: SyncIntentDao
) : SyncIntentStore, SyncIntentMaintenanceStore {

    override suspend fun getPendingByCandidateKey(candidateKey: Long) =
        intentDao.getPendingByKey(candidateKey)?.toDomain()

    override suspend fun getPendingByFeature(feature: FeatureContext): List<SyncIntent?> =
        intentDao.getPendingByFeature(feature.featureName).map { it.toDomain() }

    override suspend fun recordIntent(entry: SyncIntent) = intentDao.upsert(entry.toEntity())

    override suspend fun claimAndGetBatch(batchId: String, limit: Int): List<SyncIntent> =
        intentDao.claimAndGetBatch(batchId, limit).map { it.toDomain() }

    override suspend fun acknowledgeSuccess(batchId: String): Int =
        intentDao.updateBatchStatus(
            batchId = batchId,
            status = SyncStatus.SUCCESS,
            expectedCurrentStatus = SyncStatus.SYNCING
        )

    override suspend fun stampLastError(batchId: String, message: String) =
        intentDao.stampLastError(batchId, message)

    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------

    override suspend fun resetStaleLeases(cutOff: Long, retryThreshold: Int) =
        intentDao.resetStaleLeases(cutOff, retryThreshold)

    override suspend fun quarantineStaleLeases(cutOff: Long, retryThreshold: Int) =
        intentDao.quarantineStaleLeases(cutOff, retryThreshold)

    override suspend fun quarantineIntent(
        hlc: HLC,
        candidateKey: Long,
        errorMessage: String
    ) {
        intentDao.quarantineIntent(
            hlc = hlc.toString(),
            candidateKey = candidateKey,
            errorMessage = errorMessage
        )
    }

    override suspend fun releaseIntents(hlcs: List<HLC>): Int {
        if (hlcs.isEmpty()) return 0
        return intentDao.releaseIntents(hlcs.map { it.toString() })
    }

    override suspend fun cascadeQuarantine(): Int = intentDao.cascadeQuarantine()

    override suspend fun pruneAgedIntents(pruneAfter: Long, limit: Int) =
        intentDao.pruneByCutOff(
            cutoffMs = pruneAfter,
            limit = limit
        )

    override suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedFeatureSummary>> =
        intentDao.observeQuarantinedCountByFeature()

    override suspend fun existsForBlob(blobId: String) =
        intentDao.existsForBlobId(blobId)

}