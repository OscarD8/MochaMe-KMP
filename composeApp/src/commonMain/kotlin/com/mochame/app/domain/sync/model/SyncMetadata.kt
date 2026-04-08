package com.mochame.app.domain.sync.model

import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.MutationOp
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.infrastructure.sync.HLC

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