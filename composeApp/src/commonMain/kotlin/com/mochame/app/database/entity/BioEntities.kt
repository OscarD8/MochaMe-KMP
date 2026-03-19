package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.app.core.SyncStatus

@Entity(
    tableName = "daily_context",
    indices = [
        Index("lastModified"),
        Index("epochDay", unique = true),
        Index("syncStatus")
    ]

)
data class DailyContextEntity(
    @PrimaryKey
    val id: String,
    val epochDay: Long, // Use the 4am Rule/Epoch Day as the unique identifier
    val sleepHours: Double,
    val readinessScore: Int = 0,
    val lastModified: Long,
    val isNapped: Boolean = false,
    val syncStatus: Int = SyncStatus.PENDING.value
)