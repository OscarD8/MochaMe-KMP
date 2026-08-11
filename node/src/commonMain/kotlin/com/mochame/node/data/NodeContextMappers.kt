package com.mochame.node.data

import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.spi.node.NodeId

internal fun NodeContextEntity.toDomain() = NodeContext(
    nodeId = NodeId.parse(nodeId),
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc?.let { HLC.parse(maxHlc) },
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime,
)

internal fun NodeContext.toEntity() = NodeContextEntity(
    id = 1,
    nodeId = nodeId.toString(),
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc?.toString(),
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)