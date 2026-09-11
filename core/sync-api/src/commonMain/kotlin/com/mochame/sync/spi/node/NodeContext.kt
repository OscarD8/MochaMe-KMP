package com.mochame.sync.spi.node

import com.mochame.sync.api.hlc.HLC
import kotlin.time.Clock

data class NodeContext(
    val nodeId: NodeId,
    val appVersion: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastServerWatermark: Long? = null, // this will not prevent state drift, one for outbound and one for inbound?
    val maxHlc: HLC? = null,
    val lastServerSyncTime: Long? = null,
    val lastLocalMutationTime: Long? = null
)
