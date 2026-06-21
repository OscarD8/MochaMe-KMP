package com.mochame.bio.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_context",
    indices = [
        Index("hlc"),
        Index("epochDay", unique = true)
    ]
)
data class DailyContextEntity(
    @PrimaryKey
    val id: String,
    val hlc: String,
    val epochDay: Long,
    val sleepHours: Double,
    val readinessScore: Int = 0,
    val isNapped: Boolean = false,
    val isDeleted: Boolean = false,
    val lastModified: Long
)