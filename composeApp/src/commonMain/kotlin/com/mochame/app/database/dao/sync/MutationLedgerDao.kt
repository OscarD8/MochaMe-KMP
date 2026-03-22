package com.mochame.app.database.dao.sync

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.core.HLC
import com.mochame.app.core.SyncStatus
import com.mochame.app.database.entity.MutationEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MutationLedgerDao {

    /**
     * The Global Watermark: Hydrates the HlcFactory.
     * Room uses the HLC TypeConverter to return the object directly.
     */
    @Query("SELECT MAX(hlc) FROM mutation_ledger")
    suspend fun getGlobalMaxHlc(): HLC?

    /**
     * Compaction Lookup: Finds an unsynced mutation for a specific record.
     */
    @Query(
        """
        SELECT * FROM mutation_ledger 
        WHERE candidateKey = :candidateKey 
        AND entityType = :entityType 
        AND syncStatus = :status 
        LIMIT 1
    """)
    suspend fun getPendingMutation(
        candidateKey: String,
        entityType: String,
        status: SyncStatus = SyncStatus.PENDING
    ): MutationEntryEntity?

    // Step 1: Claim the batch with a unique ID
    @Query("""
    UPDATE mutation_ledger 
    SET syncId = :sessionId, syncStatus = :syncingStatus
    WHERE hlc IN (
        SELECT hlc FROM mutation_ledger
        WHERE syncId IS NULL 
        AND entityType = :type 
        AND syncStatus = :pendingStatus
        LIMIT :limit
    )
    """)
    suspend fun claimBatch(
        sessionId: String,
        type: String,
        limit: Int,
        pendingStatus: SyncStatus = SyncStatus.PENDING,
        syncingStatus: SyncStatus = SyncStatus.SYNCING
    ): Int

    /**
     * Batch Collector: Grabs changes to ship to the Cloud Vault.
     */
    @Query("SELECT * FROM mutation_ledger WHERE syncId = :sessionId")
    suspend fun getClaimedBatch(sessionId: String): List<MutationEntryEntity>

    /**
     * Final ACK: Marks a batch of mutations as successfully synced.
     */
    @Query("""
        UPDATE mutation_ledger 
        SET syncStatus = :status, syncId = NULL 
        WHERE hlc IN (:hlcs)
    """)
    suspend fun markAsSynced(
        hlcs: List<HLC>,
        status: SyncStatus = SyncStatus.SUCCESS
    )

    /**
     * Pruning Task: Removes old tombstones and synced history.
     */
    @Query("""
        DELETE FROM mutation_ledger 
        WHERE syncStatus = :status 
        AND createdAt < :cutoff
    """)
    suspend fun pruneOldSynced(
        cutoff: Long,
        status: SyncStatus = SyncStatus.SUCCESS
    )

    @Upsert
    suspend fun upsert(entry: MutationEntryEntity)

    @Query("DELETE FROM mutation_ledger WHERE hlc = :hlc")
    suspend fun deleteByHlc(hlc: HLC)

    @Query("SELECT COUNT(*) FROM mutation_ledger WHERE syncStatus = :status")
    fun observePendingCount(status: SyncStatus = SyncStatus.PENDING): Flow<Int>

    // ----- CLEAN UP (Janitor Support) ------
    /**
     * If any row in the entire ledger has a syncId, its stale and the result of a crash.
     * No sync should be active.
     */
    @Query("UPDATE mutation_ledger SET syncId = NULL WHERE syncId IS NOT NULL")
    suspend fun clearAllLocksForNonIdleModules()
}