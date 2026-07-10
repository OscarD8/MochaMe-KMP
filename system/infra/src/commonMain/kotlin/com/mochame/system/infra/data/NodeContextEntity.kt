package com.mochame.system.infra.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock

@Entity(tableName = "node_context")
data class NodeContextEntity(
    @PrimaryKey val id: Int = 1,
    val nodeId: String,
    val lastBootedAppVersion: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastServerSyncWatermark: String? = null,
    val maxHlc: String? = null,
    val lastServerSyncTime: Long = 0L,
    val lastLocalMutationTime: Long = 0L
)
