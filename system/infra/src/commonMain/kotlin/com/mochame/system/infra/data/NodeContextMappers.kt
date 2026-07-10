package com.mochame.system.infra.data

import com.mochame.sync.api.node.NodeContext

internal fun NodeContextEntity.toDomain() = NodeContext(
    nodeId = nodeId,
    lastBootedAppVersion = lastBootedAppVersion,
    createdAt = createdAt,
    lastServerSyncWatermark = lastServerSyncWatermark,
    maxHlc = maxHlc,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime,
)

internal fun NodeContext.toEntity() = NodeContextEntity(
    id = 1,
    nodeId = nodeId,
    lastBootedAppVersion = lastBootedAppVersion,
    createdAt = createdAt,
    lastServerSyncWatermark = lastServerSyncWatermark,
    maxHlc = maxHlc,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)