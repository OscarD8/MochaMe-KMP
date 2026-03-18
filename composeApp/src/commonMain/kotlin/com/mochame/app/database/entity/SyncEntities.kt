package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_tombstones")
data class SyncTombstoneEntity(
    @PrimaryKey val entityId: String, // The UUID of the deleted item
    val tableName: String,           // "domains", "topics", "moments"
    val deletedAt: Long              // System clock timestamp
)