package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_context",
    indices = [
        // Indexing HLC is vital for fast causality sorting
        Index("hlc"),
        // We keep epochDay unique to prevent "Double Monday" bugs
        Index("epochDay", unique = true)
    ]
)
data class DailyContextEntity(
    @PrimaryKey
    val id: String,         // Anchored: epochDay.toString()
    val hlc: String,        // Replaces lastModified logi
    val epochDay: Long,     // The 4am Rule anchor
    val sleepHours: Double,
    val readinessScore: Int = 0,
    val isNapped: Boolean = false,
    val isDeleted: Boolean = false,
    val lastModified: Long
)