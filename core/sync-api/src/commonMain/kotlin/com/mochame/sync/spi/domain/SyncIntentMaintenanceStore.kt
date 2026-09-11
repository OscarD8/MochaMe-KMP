package com.mochame.sync.spi.domain

import com.mochame.sync.spi.models.QuarantinedFeatureSummary
import kotlinx.coroutines.flow.Flow

interface SyncIntentMaintenanceStore {
    suspend fun pruneAgedIntents(pruneAfter: Long, limit: Int): Int
    suspend fun existsForBlob(blobId: String): Boolean
    suspend fun resetStaleLeases(cutOff: Long, retryThreshold: Int): Int
    suspend fun quarantineStaleLeases(cutOff: Long, retryThreshold: Int): Int
    suspend fun observeQuarantinedCountByModule(): Flow<List<QuarantinedFeatureSummary>>
}