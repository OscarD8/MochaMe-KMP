package com.mochame.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.app.core.HLC
import com.mochame.app.core.MutationOp
import com.mochame.app.core.SyncStatus

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
    val moduleName: String,         // e.g., "BIO"
    val serverWatermark: String?,   // The "Bookmark" from the Vault
    val activeSyncId: String?,      // The lock for the current session
    val syncStatus: SyncStatus,     // IDLE, SYNCING, FAILED
    val lastSyncTime: Long          // System Millis for UI display
)

@Entity(
    tableName = "mutation_ledger",
    indices = [
        // 1. For the SyncCoordinator: "Find all PENDING work"
        Index(value = ["sync_status"]),

        // 2. For the BaseRepository: "Is there a PENDING mutation for this specific record?"
        // This makes the Compaction check O(1) instead of a table scan.
        Index(value = ["entity_id", "entity_type", "sync_status"]),

        // 3. For the Pruning Worker: "Delete everything SYNCED and older than 30 days"
        Index(value = ["sync_status", "created_at"]),

        // 4. For Module Routing: "Give me only BIO mutations"
        Index(value = ["entity_type", "sync_status"])
    ]
)
data class MutationEntryEntity(
    @PrimaryKey
    val hlc: HLC,                // Changed from String to HLC
    val entityId: String,
    val entityType: String,
    val operation: MutationOp,   // Changed from Int to MutationOp (Enum)
    val syncStatus: SyncStatus,  // Changed from Int to SyncStatus (Enum)
    val syncId: String? = null,
    val createdAt: Long
)