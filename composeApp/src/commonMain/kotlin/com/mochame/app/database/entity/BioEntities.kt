package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_context",
    indices = [Index("lastModified")] // epochDay is now PK, so it's indexed by default
)
data class DailyContextEntity(
    @PrimaryKey
    val epochDay: Long, // Use the 4am Rule/Epoch Day as the unique identifier
    val sleepHours: Double,
    val readinessScore: Int = 0,
    val lastModified: Long,
    val isNapped: Boolean = false
)