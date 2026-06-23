package com.mochame.sync.contract.models

import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.SyncStatus

data class SyncIntent(
    val hlc: HLC,
    val candidateKey: String,
    val module: String,
    val model: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val createdAt: Long,
    val syncId: String? = null,
    val payload: ByteArray? = null,
    val diagnosticSummary: String? = null,
    val overflowBlobId: String? = null,
    val leasedAt: Long? = null,
    val lastErrorMessage: String? = null
)