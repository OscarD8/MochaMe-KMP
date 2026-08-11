package com.mochame.bio.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.sync.common.TriState

@Entity(
    tableName = "daily_context",
    indices = [
        Index("hlc")
    ]
)
data class DailyContextEntity(
    @PrimaryKey
    val id: Long,
    val hlc: String,
    val sleepHours: Double,
    val readinessScore: Int = 0,
    val isNapped: TriState = TriState.UNSET,
    val isDeleted: Boolean = false,
    val fieldHlcs: ByteArray = ByteArray(0),
    val lastModified: Long
)