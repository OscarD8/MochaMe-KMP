package com.mochame.app.domain.model

import com.mochame.app.core.MochaModule
import com.mochame.app.core.SyncStatus

data class SyncMetadata(
    val module: MochaModule,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val status: SyncStatus,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)