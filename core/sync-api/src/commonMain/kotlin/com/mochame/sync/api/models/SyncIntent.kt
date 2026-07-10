package com.mochame.sync.api.models

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus

data class SyncIntent(
    val featureSchemaVersion: Int,
    val hlc: HLC,
    val candidateKey: String,
    val module: String,
    val model: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val retryCount: Int = 0,
    val createdAt: Long,
    val syncId: String? = null,
    val payload: ByteArray? = null,
    val diagnosticSummary: String? = null,
    val overflowBlobId: String? = null,
    val leasedAt: Long? = null,
    val lastErrorMessage: String? = null
)