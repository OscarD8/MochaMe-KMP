package com.mochame.sync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.models.HLC
import com.mochame.sync.domain.model.QuarantinedModuleSummary
import kotlinx.coroutines.flow.Flow

/**
 * The purpose of this DAO is to ensure the system can maintain a reliable, predictable
 * record of what individual changes must leave the device and what states they are currently in. (With
 * the additional case where external intents will be stored if they hold an overflowBlobId).
 * Responsibilities involve:
 * - Ingestion & Ordering, Leasing & Batching, State Management & Recovery.
 *
 *  On ingestion, it must accept local modifications from features, and order those mutations in
 * way that ensures outbound batching reaches the longest pending intents first.
 * This component must ensure an atomic claim phase where no two sessions should ever grab
 * overlapping intents. It must ensure Success states clear the sessionID and updates the status.
 * On failure, it allows a quarantining protocol and makes transparent the failure state.
 * The design should allow the synchronization system to recover from unexpected
 * application terminations, whilst managing the footprint of intent records themselves.
 */
@Dao
interface SyncIntentDao {

    /**
     * Compaction Lookup. Finds an unsynced mutation for a specific record.
     */
    @Query(
        """
        SELECT * FROM SyncIntentEntity 
        WHERE candidateKey = :candidateKey 
        AND syncStatus = :status 
        LIMIT 1
    """
    )
    suspend fun getPendingByKey(
        candidateKey: String,
        status: SyncStatus = SyncStatus.PENDING
    ): SyncIntentEntity?

    @Query(
        """
        SELECT * FROM SyncIntentEntity
        WHERE module = :module
        AND syncStatus = :status
    """
    )
    suspend fun getPendingByModule(
        module: String,
        status: SyncStatus = SyncStatus.PENDING
    ): List<SyncIntentEntity>

    @Query(
        """
        UPDATE SyncIntentEntity 
        SET syncId = :id, syncStatus = :syncingStatus
        WHERE hlc IN (
            SELECT hlc FROM SyncIntentEntity
            WHERE syncId IS NULL 
            AND syncStatus = :pendingStatus
            ORDER BY hlc ASC
            LIMIT :limit
        )
    """
    )
    suspend fun claimBatch(
        id: String,
        limit: Int,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): Int

    @Query("SELECT * FROM SyncIntentEntity WHERE syncId = :id ORDER BY hlc ASC")
    suspend fun getClaimedBatch(id: String): List<SyncIntentEntity>

    @Query(
        """
        UPDATE SyncIntentEntity 
        SET syncStatus = :status, syncId = NULL 
        WHERE hlc IN (:hlcs)
    """
    )
    suspend fun markAsSynced(
        hlcs: List<HLC>,
        status: SyncStatus = SyncStatus.SUCCESS
    )

    @Query(
        """
            UPDATE SyncIntentEntity
            SET syncStatus = :status
            WHERE hlc = :hlc    
        """
    )
    suspend fun setStatus(hlc: HLC, status: SyncStatus)

    @Query("SELECT EXISTS(SELECT 1 FROM SyncIntentEntity WHERE overflowBlobId = :blobId)")
    suspend fun existsByBlobId(blobId: String): Boolean

    @Query(
        """
    DELETE FROM SyncIntentEntity 
        WHERE hlc IN (
            SELECT hlc FROM SyncIntentEntity
            WHERE syncStatus = :status 
            AND createdAt < :cutoff
            LIMIT :limit
        )
    """
    )
    suspend fun pruneOldSynced(
        cutoff: Long,
        status: SyncStatus = SyncStatus.SUCCESS,
        limit: Int
    ): Int

    @Upsert
    suspend fun upsert(entry: SyncIntentEntity)

    @Query("DELETE FROM SyncIntentEntity WHERE hlc = :hlc")
    suspend fun deleteByHlc(hlc: HLC)

    // ----- CLEAN UP (Janitor Support) ------
    /**
     * If any row in the entire ledger has a syncId, its stale and the result of a crash.
     * No sync should be active.
     */
    @Query(
        """
        UPDATE SyncIntentEntity 
        SET syncId = NULL, syncStatus = :desiredStatus 
        WHERE syncId IS NOT NULL
    """
    )
    suspend fun clearAllLocksAndResetToPending(
        desiredStatus: SyncStatus = SyncStatus.PENDING
    ): Int

    @Query(
        """
        UPDATE SyncIntentEntity
        SET lastErrorMessage = :message
        WHERE hlc IN (:hlcs)
    """
    )
    suspend fun stampLastError(hlcs: List<String>, message: String)

    @Query(
        """
        UPDATE SyncIntentEntity
        SET syncStatus = :status, retryCount = :retryCount
        WHERE hlc = :hlc    
        """
    )
    suspend fun quarantineIntent(
        hlc: HLC,
        retryCount: Int,
        status: SyncStatus = SyncStatus.QUARANTINED
    )

    /**
     * Useful for targeting intents that may have switched to a syncing status but
     * never shipped due to an application crash for example. The cutoff is
     * what determines whether the intent is still perceived to be part of an active
     * process, or is now recognized as stale.
     */
    @Query(
        """
        SELECT * FROM SyncIntentEntity
        WHERE syncId IS NOT NULL AND syncStatus = :targetStatus AND leasedAt < :cutOff
        """
    )
    suspend fun getStaleLeasedIntents(
        cutOff: Long,
        targetStatus: SyncStatus = SyncStatus.SYNCING,
    ): List<SyncIntentEntity>

    /**
     * Updates the retry count as requested, and defaults to setting the status back to
     * [SyncStatus.PENDING].
     */
    @Query(
        """
        UPDATE SyncIntentEntity
        SET retryCount = :retryCount, syncStatus = :resetStatus, syncId = NULL
        WHERE hlc = :hlc
    """
    )
    suspend fun resetLease(
        hlc: HLC,
        retryCount: Int,
        resetStatus: SyncStatus = SyncStatus.PENDING
    )

    @Query("""
        SELECT module, COUNT(*) AS count
        FROM SyncIntentEntity
        WHERE syncStatus = :quarantinedStatus 
        GROUP BY module
    """)
    fun observeQuarantinedCountByModule(quarantinedStatus: SyncStatus = SyncStatus.QUARANTINED): Flow<List<QuarantinedModuleSummary>>
}