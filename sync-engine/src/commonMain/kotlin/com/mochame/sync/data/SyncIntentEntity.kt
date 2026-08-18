package com.mochame.sync.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import kotlin.time.Clock

/**
 * Sync metadata wrapping each local intent. This model extends on the domain model [DecodeContext]
 * to extend conflict resolution capabilities.
 * Idea is for this to act as a persistence record of a mutation's lifecycle.
 */
@Entity(
    indices = [
        Index(value = ["syncStatus"]),
        Index(value = ["syncStatus", "leasedAt"]),
        Index(value = ["featureContext", "syncStatus"]),
        Index(value = ["syncId"])
    ]
)
data class SyncIntentEntity(
    @PrimaryKey val hlc: String,
    val featureSchemaVersion: Int,
    val candidateKey: Long,
    val featureContext: FeatureContext,
    val operation: MutationOp,
    val payload: ByteArray?,
    val overflowBlobId: String?,
    val syncStatus: SyncStatus,
    val syncId: String? = null,          // lease identity, diagnostic traceability
    val leasedAt: Long? = null,          // enables safe Janitor cutoff queries
    val diagnosticSummary: String?,
    val retryCount: Int = 0,              // Janitor implements threshold logic
    val lastErrorMessage: String? = null,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)