package com.mochame.app.database.dao

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
    @Query("""
        SELECT * FROM mutation_ledger 
        WHERE entityId = :entityId 
        AND entityType = :entityType 
        AND syncStatus = :status 
        LIMIT 1
    """)
    suspend fun getPendingMutation(
        entityId: String,
        entityType: String,
        status: SyncStatus = SyncStatus.PENDING
    ): MutationEntryEntity?

    /**
     * Batch Collector: Grabs changes to ship to the Cloud Vault.
     */
    @Query("""
        SELECT * FROM mutation_ledger 
        WHERE entityType = :entityType 
        AND syncStatus = :status 
        ORDER BY hlc ASC 
        LIMIT :limit
    """)
    suspend fun getNextBatch(
        entityType: String,
        limit: Int,
        status: SyncStatus = SyncStatus.PENDING
    ): List<MutationEntryEntity>

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

    @Delete
    suspend fun deleteByHlc(hlc: HLC)

    @Query("SELECT COUNT(*) FROM mutation_ledger WHERE syncStatus = :status")
    fun observePendingCount(status: SyncStatus = SyncStatus.PENDING): Flow<Int>

    // ----- CLEAN UP (Janitor Support) ------

    @Query("""
        UPDATE mutation_ledger 
        SET syncStatus = :targetStatus, syncId = NULL 
        WHERE entityType = :moduleName AND syncId = :staleSyncId
    """)
    suspend fun unlockOrphanedRecords(
        moduleName: String,
        staleSyncId: String,
        targetStatus: SyncStatus = SyncStatus.PENDING
    )

    @Query("""
        UPDATE mutation_ledger 
        SET syncStatus = :targetStatus, syncId = NULL 
        WHERE entityType = :moduleName AND syncId IS NOT NULL
    """)
    suspend fun clearAllStaleLocks(
        moduleName: String,
        targetStatus: SyncStatus = SyncStatus.PENDING
    )
}