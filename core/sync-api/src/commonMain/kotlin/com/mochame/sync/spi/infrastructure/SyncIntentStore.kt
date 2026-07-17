package com.mochame.sync.spi.infrastructure

import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.models.SyncIntent

interface SyncIntentStore {
    suspend fun getPendingByCandidateKey(candidateKey: String): SyncIntent?

    suspend fun recordIntent(entry: SyncIntent)
    suspend fun getPendingByFeature(feature: FeatureContext): List<SyncIntent?>
    suspend fun discardIntent(hlc: HLC)
    suspend fun claimBatch(batchId: String, limit: Int = 50): Int
    suspend fun getClaimedBatch(batchId: String): List<SyncIntent>

    suspend fun acknowledgeSuccess(hlcList: List<HLC>)
    suspend fun stampLastError(hlcs: List<HLC>, message: String)
}