package com.mochame.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.MutationOp
import com.mochame.app.domain.sync.utils.SyncStatus
import com.mochame.app.infrastructure.sync.HLC

@Entity(
    tableName = "sync_tombstones",
    indices = [
        Index(value = ["syncId"]), // Critical for batch updates
        Index(value = ["tableName", "deletedAt"]) // Critical for delta fetches
    ]
)
data class SyncTombstoneEntity(
    @PrimaryKey val candidateKey: String,
    val tableName: String,
    val hlc: HLC,
    val deletedAt: Long,
    val syncId: String? = null,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val moduleName: MochaModule,                        // e.g., "BIO"
    val serverWatermark: String? = null,              // The "Bookmark" from the Vault
    val localMaxHlc: String? = null,                 // The highest HLC this module has ever seen
    val syncId: String? = null,                     // The lock for the current session
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastServerSyncTime: Long = 0L,                // Wall-clock of the last successful 200 OK
    val lastLocalMutationTime: Long = 0L         // Wall-clock of the last local HLC generation
)

@Entity(
    tableName = "mutation_ledger",
    indices = [
        // 1. For the SyncCoordinator: "Find all PENDING work"
        Index(value = ["syncStatus"]),

        // 2. For the BaseRepository: "Is there a PENDING mutation for this specific record?"
        // This makes the Compaction check O(1) instead of a table scan.
        Index(value = ["candidateKey", "entityType", "syncStatus"]),

        // 3. For the Pruning Worker: "Delete everything SYNCED and older than 30 days"
        Index(value = ["syncStatus", "createdAt"]),

        // 4. For Module Routing: "Give me only BIO mutations"
        Index(value = ["entityType", "syncStatus"]),

        // 5. Records scanned after 200 OK
        Index(value = ["syncId"])
    ]
)
data class MutationEntryEntity(
    @PrimaryKey
    val hlc: HLC,
    val candidateKey: String,
    val entityType: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val syncId: String? = null,
    val hasConflict: Boolean = false,
    val retryCount: Int = 0,
    val createdAt: Long
)