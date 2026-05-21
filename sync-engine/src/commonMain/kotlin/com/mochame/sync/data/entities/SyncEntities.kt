package com.mochame.sync.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.domain.state.SyncStatus
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
        Index(value = ["syncStatus"]),
        Index(value = ["candidateKey", "module", "syncStatus"]),
        Index(value = ["syncStatus", "createdAt"]),
        Index(value = ["module", "syncStatus"]),
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