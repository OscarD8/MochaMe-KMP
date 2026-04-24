package com.mochame.sync.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.metadata.MochaModule
import com.mochame.metadata.MutationOp
import com.mochame.sync.domain.SyncStatus
import kotlin.time.Clock

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val module: MochaModule,
    val serverWatermark: String? = null,
    val localMaxHlc: String? = null,
    val syncId: String? = null,
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
        Index(value = ["candidateKey", "module", "syncStatus"]),
        // 3. For the Pruning Worker: "Delete everything SYNCED and older than 30 days"
        Index(value = ["syncStatus", "createdAt"]),
        // 4. For Module Routing: "Give me only BIO mutations"
        Index(value = ["module", "syncStatus"]),
        // 5. Records scanned after 200 OK
        Index(value = ["syncId"])
    ]
)
data class SyncIntentEntity(
    @PrimaryKey val hlc: String,
    val candidateKey: String,
    val module: MochaModule,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val syncId: String? = null,
    val payload: ByteArray?,
    val diagnosticSummary: String?,
    val overflowBlobId: String?,
    val hasConflict: Boolean = false,
    val retryCount: Int = 0,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)