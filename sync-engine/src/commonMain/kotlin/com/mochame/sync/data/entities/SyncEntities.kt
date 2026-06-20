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
 * For quick lookups of max HLC values as boot and server watermarks.
 * Note - no longer using this as an attempt to perform quick sweep of
 * failing sync intents, in shift to using the SyncIntent states themselves
 * and flows observing statuses.
 */
@Entity
data class SyncModuleStateEntity(
    @PrimaryKey
    val module: MochaModule,
    val serverWatermark: String? = null,
    val moduleMaxHlc: String? = null,
    val syncId: String? = null,
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
    val payload: ByteArray?,
    val overflowBlobId: String?,
    val syncStatus: SyncStatus,
    val syncId: String? = null,          // lease identity, diagnostic traceability
    val leasedAt: Long? = null,          // enables safe Janitor cutoff queries
    val diagnosticSummary: String?,
    val retryCount: Int = 0,              // Janitor implements threshold logic
    val lastErrorMessage: String? = null, // works in conjunction with retryCount
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
    // hasConflict removed — until server conflict protocol is defined
)