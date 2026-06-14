package com.mochame.sync.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.domain.state.SyncStatus
import kotlin.time.Clock
import com.mochame.sync.domain.model.DecodeContext

/**
 * For module level determination of sync status.
 */
@Entity
data class SyncModuleStateEntity(
    @PrimaryKey
    val module: MochaModule,
    val serverWatermark: String? = null,
    val moduleMaxHlc: String? = null,
    val syncId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastServerSyncTime: Long = 0L,           // Wall-clock of the last successful 200 OK
    val lastLocalMutationTime: Long = 0L         // Wall-clock of the last local HLC generation
)


/**
 * Sync metadata wrapping each local intent. This model extends on the domain model [DecodeContext]
 * to extend conflict resolution capabilities.
 * Idea is for this to act as a persistence record of a mutation's lifecycle.
 */
@Entity(
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
    val model: String,
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