package com.mochame.sync.api.node

data class NodeContext(
    val nodeId: String,
    val lastBootedAppVersion: Int,
    val createdAt: Long,
    val lastServerSyncWatermark: String?,
    val maxHlc: String?,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)
