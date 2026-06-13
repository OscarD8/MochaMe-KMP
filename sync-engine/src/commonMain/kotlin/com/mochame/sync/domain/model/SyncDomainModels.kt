package com.mochame.sync.domain.model


import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.HLC
import com.mochame.sync.domain.state.SyncStatus
import com.mochame.sync.infrastructure.LocalFirstRepository

data class SyncModuleState(
    val module: MochaModule,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val status: SyncStatus,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)

data class SyncIntent(
    val hlc: HLC,
    val candidateKey: String,
    val module: MochaModule,
    val model: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val syncId: String?,
    val payload: ByteArray?,
    val diagnosticSummary: String?,
    val overflowBlobId: String?,
    val hasConflict: Boolean,
    val retryCount: Int,
    val createdAt: Long
)

/**
 * Fields existing in the synced payload that are required to reconstruct the model payload itself.
 * Currently used for decoding a payload in [LocalFirstRepository].
 */
data class EntityMetadata(
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)