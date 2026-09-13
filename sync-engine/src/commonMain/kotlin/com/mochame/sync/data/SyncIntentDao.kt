package com.mochame.sync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.sync.api.hlc.HLC
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

    /**
     * For an entity to be valid for a batch (and therefore synchronization) it must:
     * - Have no current lease
     * - Be in a pending status
     * - Have no prior intent in any status other than Success
     *
     * This is to prevent a device making multiple local mutations to a model and
     * syncing partial states. Either a model exists in parity across the distributed
     * system, or the local device is the only location where the corruption is held.
     * Quarantined intents are therefore causally contagious to their model.
     */
    @Query(
        """
    UPDATE SyncIntentEntity 
    SET batchId = :id, 
        syncStatus = :syncingStatus, 
        leasedAt = :leasedAt
    WHERE hlc IN (
        SELECT candidate.hlc 
        FROM SyncIntentEntity candidate
        WHERE candidate.batchId IS NULL 
          AND candidate.syncStatus = :pendingStatus
          
          AND NOT EXISTS (
              SELECT 1 FROM SyncIntentEntity q
              WHERE q.candidateKey = candidate.candidateKey
                AND q.syncStatus = :quarantinedStatus
          )
          
          AND NOT EXISTS (
              SELECT 1 FROM SyncIntentEntity prior
              WHERE prior.candidateKey = candidate.candidateKey
                AND prior.hlc < candidate.hlc
                AND prior.syncStatus != :successStatus
          )
          
        ORDER BY candidate.hlc ASC
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
        quarantinedStatus: SyncStatus = SyncStatus.QUARANTINED,
        successStatus: SyncStatus = SyncStatus.SUCCESS
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

    @Query(
        """
    UPDATE SyncIntentEntity
    SET syncStatus = :quarantineStatus,
        lastErrorMessage = :errorMessage
    WHERE hlc = :hlc AND candidateKey = :candidateKey
    """
    )
    suspend fun quarantineIntent(
        hlc: String,
        candidateKey: Long,
        errorMessage: String,
        quarantineStatus: SyncStatus = SyncStatus.QUARANTINED
    ): Int

    @Query(
        """
    UPDATE SyncIntentEntity
    SET syncStatus = :pendingStatus,
        batchId = NULL,
        leasedAt = NULL
    WHERE hlc IN (:hlcs)
    """
    )
    suspend fun releaseIntents(
        hlcs: List<String>,
        pendingStatus: SyncStatus = SyncStatus.PENDING
    ): Int

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
    SET syncStatus = :quarantineStatus,
        lastErrorMessage = 'Cascaded quarantine: causal predecessor failed on candidateKey'
    WHERE syncStatus = :pendingStatus
      AND candidateKey IN (
          SELECT DISTINCT candidateKey 
          FROM SyncIntentEntity 
          WHERE syncStatus = :quarantineStatus
      )
    """
    )
    suspend fun cascadeQuarantine(
        pendingStatus: SyncStatus = SyncStatus.PENDING,
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