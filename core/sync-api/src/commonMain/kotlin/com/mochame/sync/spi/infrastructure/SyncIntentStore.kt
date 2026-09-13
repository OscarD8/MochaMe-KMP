package com.mochame.sync.spi.infrastructure

import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.models.SyncIntent

interface SyncIntentStore {
    suspend fun getPendingByCandidateKey(candidateKey: Long): SyncIntent?

    suspend fun recordIntent(entry: SyncIntent)
    suspend fun getPendingByFeature(feature: FeatureContext): List<SyncIntent?>
    suspend fun claimAndGetBatch(batchId: String, limit: Int = 50): List<SyncIntent>

    suspend fun acknowledgeSuccess(batchId: String): Int

}