package com.mochame.sync.domain.model


import com.mochame.metadata.MochaModule
import com.mochame.metadata.MutationOp
import com.mochame.sync.domain.SyncStatus
import com.mochame.sync.infrastructure.HLC

data class SyncMetadata(
    val module: MochaModule,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val status: SyncStatus,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)

/**
 * These are the fields stored in flat SQL columns, not the binary blob.
 */
data class EntityMetadata(
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)