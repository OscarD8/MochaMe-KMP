package com.mochame.system.infra.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock

@Entity(tableName = "node_identity")
data class NodeIdentityEntity(
    @PrimaryKey val id: Int = 1,
    val nodeId: String,
    val lastBootedAppVersion: Int,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)