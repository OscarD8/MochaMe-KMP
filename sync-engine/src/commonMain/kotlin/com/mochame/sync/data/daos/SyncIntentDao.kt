package com.mochame.sync.data.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.sync.contract.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.domain.model.QuarantinedModuleSummary
import com.mochame.sync.domain.state.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncIntentDao {

    /**
     * The Global Watermark: Hydrates the HlcFactory.
     * Room uses the HLC TypeConverter to return the object directly.
     */
    @Query("SELECT MAX(hlc) FROM SyncIntentEntity")
    suspend fun getLedgerGlobalMaxHlc(): String?

    /**
     * Compaction Lookup: Finds an unsynced mutation for a specific record.
     */
    @Query(
        """
        SELECT * FROM SyncIntentEntity 
        WHERE candidateKey = :candidateKey 
        AND module = :module 
        AND syncStatus = :status 
        LIMIT 1
    """
    )
    suspend fun getPendingByKey(
        candidateKey: String,
        module: String,
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
        SET syncId = :sessionId, syncStatus = :syncingStatus
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
        sessionId: String,
        limit: Int,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): Int

    /**
     * Batch Collector: Grabs changes to ship to the Cloud Vault.
     */
    @Query("SELECT * FROM SyncIntentEntity WHERE syncId = :sessionId")
    suspend fun getClaimedBatch(sessionId: String): List<SyncIntentEntity>

    // using transaction provider instead
//    @Transaction
//    suspend fun claimAndFetchBatch(
//        sessionId: String,
//        entityType: MochaModule,
//        limit: Int
//    ): List<SyncIntentEntity> {
//        val rowsAffected = claimBatch(sessionId, entityType, limit)
//        if (rowsAffected == 0) return emptyList()
//
//        return getClaimedBatch(sessionId)
//    }

    /**
     * Final ACK: Marks a batch of mutations as successfully synced.
     */
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

    /**
     * Pruning Task: Removes old tombstones and synced history.
     */
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
    suspend fun deleteByHlc(hlc: String)

    @Query("SELECT COUNT(*) FROM SyncIntentEntity WHERE syncStatus = :status")
    fun observePendingCount(status: SyncStatus = SyncStatus.PENDING): Flow<Int>

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
    suspend fun clearAllLocksAndResetStatus(
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
        hlc: String,
        retryCount: Int,
        status: SyncStatus = SyncStatus.QUARANTINED
    )

    @Query(
        """
        SELECT * FROM SyncIntentEntity
        WHERE syncId != NULL AND syncStatus = :targetStatus AND leasedAt > :cutOff
        """
    )
    suspend fun getStaleLeasedIntents(
        cutOff: Long,
        targetStatus: SyncStatus = SyncStatus.SYNCING,
    ): List<SyncIntentEntity>

    @Query(
        """
        UPDATE SyncIntentEntity
        SET retryCount = :retryCount, syncStatus = :resetStatus, syncId = NULL
        WHERE hlc = :hlc
    """
    )
    suspend fun resetLease(
        hlc: String,
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