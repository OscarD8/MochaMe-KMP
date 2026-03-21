package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Clock

@Entity(tableName = "global_settings")
data class GlobalSettingsEntity(
    @PrimaryKey val id: Int = 1, // We only ever want one row
    val nodeId: String,          // The "Actor" ID for HLCs
    val lastAppVersion: Int,      // Useful for migrations later
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)