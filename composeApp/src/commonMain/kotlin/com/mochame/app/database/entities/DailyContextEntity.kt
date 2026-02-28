package com.mochame.app.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_context",
    indices = [Index(value = ["epochDay"], unique = true)] //
)
data class DailyContextEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val sleepHours: Double,
    val lastModified: Long // Required for Phase 3 Syncing
)