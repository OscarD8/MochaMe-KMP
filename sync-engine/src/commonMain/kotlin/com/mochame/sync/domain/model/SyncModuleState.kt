package com.mochame.sync.domain.model

internal data class SyncModuleState(
    val module: String,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)