package com.mochame.sync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.spi.models.QuarantinedFeatureSummary
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * The purpose of this DAO is to ensure the system can maintain a reliable, predictable
 * record of what individual changes must leave the device and what states they are currently in. (With
 * the additional case where external intents will be stored if they hold an overflowBlobId).
 * Responsibilities involve:
 * - Ingestion & Ordering, Leasing & Batching, State Management & Recovery.
 *
 *  On ingestion, it must accept local modifications from featureContexts, and order those mutations in
 * way that ensures outbound batching reaches the longest pending intents first.
 * This component must ensure an atomic claim phase where no two sessions should ever grab
 * overlapping intents. It must ensure Success states clear the sessionID and updates the status.
 * On failure, it allows a quarantining protocol and makes transparent the failure state.
 * The design should allow the synchronization system to recover from unexpected
 * application terminations, whilst managing the footprint of intent records themselves.
 */
@Dao
interface SyncIntentDao {

    @Upsert
    suspend fun upsert(entry: SyncIntentEntity)

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
        candidateKey: Long,
        status: SyncStatus = SyncStatus.PENDING
    ): SyncIntentEntity?

    @Query(
        """
        SELECT * FROM SyncIntentEntity
        WHERE featureContext = :featureContextName
        AND syncStatus = :status
    """
    )
    suspend fun getPendingByFeature(
        featureContextName: String,
        status: SyncStatus = SyncStatus.PENDING
    ): List<SyncIntentEntity>

    @Query(
        """
        UPDATE SyncIntentEntity 
        SET batchId = :id, syncStatus = :syncingStatus, leasedAt = :leasedAt
        WHERE hlc IN (
            SELECT hlc FROM SyncIntentEntity
            WHERE batchId IS NULL 
            AND syncStatus = :pendingStatus
            ORDER BY hlc ASC
            LIMIT :limit
        )
    """
    )
    suspend fun claimBatch(
        id: String,
        limit: Int,
        leasedAt: Long,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING,
    ): Int

    @Query("SELECT * FROM SyncIntentEntity WHERE batchId = :id ORDER BY hlc ASC")
    suspend fun getClaimedBatch(id: String): List<SyncIntentEntity>

    @Transaction
    suspend fun claimAndGetBatch(
        id: String,
        limit: Int,
        leasedAt: Long = Clock.System.now().toEpochMilliseconds(),
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): List<SyncIntentEntity> {
        val claimed = claimBatch(id, limit, leasedAt, pendingStatus, syncingStatus)
        if (claimed == 0) return emptyList()
        return getClaimedBatch(id)
    }

    @Query(
        """
            UPDATE SyncIntentEntity 
            SET syncStatus = :status
            WHERE batchId = :batchId 
              AND syncStatus = :expectedCurrentStatus
    """
    )
    suspend fun updateBatchStatus(
        batchId: String,
        status: SyncStatus,
        expectedCurrentStatus: SyncStatus
    ): Int

    @Query(
        """
        UPDATE SyncIntentEntity
        SET lastErrorMessage = :message
        WHERE batchId = :batchId
    """
    )
    suspend fun stampLastError(batchId: String, message: String)

    // ----- CLEAN UP (Janitor Support) ------
    @Query("SELECT EXISTS(SELECT 1 FROM SyncIntentEntity WHERE overflowBlobId = :blobId)")
    suspend fun existsForBlobId(blobId: String): Boolean

    @Query(
        """
    UPDATE SyncIntentEntity
    SET retryCount = retryCount + 1,
        syncStatus = :quarantineStatus
    WHERE batchId IS NOT NULL 
      AND syncStatus = :targetStatus 
      AND leasedAt <= :cutOff
      AND (retryCount + 1) >= :retryThreshold
    """
    )
    suspend fun quarantineStaleLeases(
        cutOff: Long,
        retryThreshold: Int,
        targetStatus: SyncStatus = SyncStatus.SYNCING,
        quarantineStatus: SyncStatus = SyncStatus.QUARANTINED
    ): Int

    @Query(
        """
    UPDATE SyncIntentEntity
    SET retryCount = retryCount + 1,
        syncStatus = :resetStatus,
        batchId = NULL,
        leasedAt = NULL
    WHERE batchId IS NOT NULL 
      AND syncStatus = :targetStatus 
      AND leasedAt <= :cutOff
      AND (retryCount + 1) < :retryThreshold
    """
    )
    suspend fun resetStaleLeases(
        cutOff: Long,
        retryThreshold: Int,
        targetStatus: SyncStatus = SyncStatus.SYNCING,
        resetStatus: SyncStatus = SyncStatus.PENDING
    ): Int

    @Query(
        """
        SELECT featureContext, COUNT(*) AS count
        FROM SyncIntentEntity
        WHERE syncStatus = :quarantinedStatus 
        GROUP BY featureContext
    """
    )
    fun observeQuarantinedCountByFeature(quarantinedStatus: SyncStatus = SyncStatus.QUARANTINED): Flow<List<QuarantinedFeatureSummary>>

    @Query(
        """
    DELETE FROM SyncIntentEntity 
        WHERE hlc IN (
            SELECT hlc FROM SyncIntentEntity
            WHERE syncStatus = :status 
            AND createdAt < :cutoffMs
            LIMIT :limit
        )
    """
    )
    suspend fun pruneByCutOff(
        cutoffMs: Long,
        status: SyncStatus = SyncStatus.SUCCESS,
        limit: Int
    ): Int

}