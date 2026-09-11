package com.mochame.sync.spi.models

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.FeatureContext

data class SyncIntent(
    val featureSchemaVersion: Int,
    val hlc: HLC,
    val candidateKey: Long,
    val featureContext: FeatureContext,
    val operation: MutationOp,
    val createdAt: Long,
    val changedMask: Long,
    val batchId: String? = null,
    val payload: ByteArray? = null,
    val diagnosticSummary: String? = null,
    val overflowBlobId: String? = null,
    val syncStatus: SyncStatus,
    val retryCount: Int = 0,
    val leasedAt: Long? = null,
    val lastErrorMessage: String? = null
)