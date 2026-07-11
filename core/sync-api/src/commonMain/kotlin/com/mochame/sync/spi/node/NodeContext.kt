package com.mochame.sync.spi.node

import com.mochame.sync.api.models.HLC
import kotlin.time.Clock

data class NodeContext(
    val nodeId: String,
    val appVersion: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastServerWatermark: String? = null,
    val maxHlc: HLC? = null,
    val lastServerSyncTime: Long? = null,
    val lastLocalMutationTime: Long? = null
)
