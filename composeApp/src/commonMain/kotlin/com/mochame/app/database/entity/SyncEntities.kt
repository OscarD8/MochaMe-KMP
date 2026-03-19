package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_tombstones",
    indices = [
        Index(value = ["syncId"]), // Critical for batch updates
        Index(value = ["tableName", "deletedAt"]) // Critical for delta fetches
    ]
)
data class SyncTombstoneEntity(
    @PrimaryKey val entityId: String,
    val tableName: String,
    val deletedAt: Long,
    val syncId: String? = null,
    val retryCount: Int = 0          // Circuit breaker for "Poison" records
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val moduleName: String,         // e.g., "bio_data", "moments", "telemetry"
    val lastWatermark: String?,     // The Opaque Watermark from the Vault
    val activeSessionId: String?,   // The sessionUUID currently in flight
    val lastSyncStatus: Int,        // 0: Idle, 1: Syncing, 2: Failed
    val lastSyncTime: Long          // Last successful sync (System Millis)
)