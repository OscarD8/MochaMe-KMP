package com.mochame.node.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock

@Entity(tableName = "node_context")
data class NodeContextEntity(
    @PrimaryKey val id: Int = 1,
    val nodeId: String,
    val appVersion: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastServerWatermark: String? = null,
    val maxHlc: String? = null,
    val lastServerSyncTime: Long? = null,
    val lastLocalMutationTime: Long? = null
)
