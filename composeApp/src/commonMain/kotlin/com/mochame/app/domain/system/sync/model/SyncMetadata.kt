package com.mochame.app.domain.system.sync.model

import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.domain.system.sync.utils.SyncStatus

data class SyncMetadata(
    val module: MochaModule,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val status: SyncStatus,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)