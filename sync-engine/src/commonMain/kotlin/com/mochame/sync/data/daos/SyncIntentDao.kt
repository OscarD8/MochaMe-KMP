package com.mochame.sync.data.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.contract.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
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
        module: MochaModule,
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
        module: MochaModule,
        status: SyncStatus = SyncStatus.PENDING
    ): List<SyncIntentEntity>

    // Step 1: Claim the batch with a unique ID
    @Query(
        """
        UPDATE SyncIntentEntity 
        SET syncId = :sessionId, syncStatus = :syncingStatus
        WHERE hlc IN (
            SELECT hlc FROM SyncIntentEntity
            WHERE syncId IS NULL 
            AND module = :entityType 
            AND syncStatus = :pendingStatus
            ORDER BY hlc ASC
            LIMIT :limit
        )
    """
    )
    suspend fun claimBatch(
        sessionId: String,
        entityType: MochaModule,
        limit: Int,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): Int

    /**
     * Batch Collector: Grabs changes to ship to the Cloud Vault.
     */
    @Query("SELECT * FROM SyncIntentEntity WHERE syncId = :sessionId")
    suspend fun getClaimedBatch(sessionId: String): List<SyncIntentEntity>

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

}