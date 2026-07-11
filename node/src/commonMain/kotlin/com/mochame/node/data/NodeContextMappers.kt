package com.mochame.node.data

import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.node.NodeContext

internal fun NodeContextEntity.toDomain() = NodeContext(
    nodeId = nodeId,
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc?.let { HLC.parse(maxHlc) },
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime,
)

internal fun NodeContext.toEntity() = NodeContextEntity(
    id = 1,
    nodeId = nodeId,
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc?.toString(),
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)