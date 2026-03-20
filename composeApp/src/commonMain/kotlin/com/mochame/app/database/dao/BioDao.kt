package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BioDao {

    /**
     * The Standard Write: Persists local changes.
     * Used by the [BaseRepository] block.
     */
    @Upsert
    suspend fun upsert(context: DailyContextEntity)

    /**
     * The Conflict Resolver:
     * Handles incoming Cloud changes by comparing Hybrid Logical Clocks.
     * This method handles both remote updates and remote tombstones (deletions).
     */
    @Transaction
    suspend fun resolveSync(incoming: DailyContextEntity) {
        val local = getContextById(incoming.id)

        // 1. If no local record exists, or the incoming HLC is logically "newer"
        //    (Alphabetical comparison works perfectly for HLC strings)
        if (local == null || incoming.hlc > local.hlc) {
            upsert(incoming)
        }
    }

    /**
     * Logical Delete:
     * We no longer use 'DELETE FROM'. We flip the bit and update the HLC.
     */
    @Query("""
        UPDATE daily_context 
        SET isDeleted = 1, hlc = :newHlc, lastModified = :timestamp 
        WHERE id = :id
    """)
    suspend fun markAsDeleted(id: String, newHlc: String, timestamp: Long): Int

    /**
     * THE PRUNING PROTOCOL:
     * Physically removes tombstones that have been synced to the cloud
     * and aged out of the 30-day "Dissemination Window."
     * * @param cutoff The physical timestamp (System.now() - 30.days)
     */
    @Query("""
        DELETE FROM daily_context 
        WHERE isDeleted = 1 
        AND lastModified < :cutoff
    """)
    suspend fun hardDeletePruning(cutoff: Long)

    /**
     * Integrity Check:
     * Returns the count of tombstones currently being held.
     */
    @Query("SELECT COUNT(*) FROM daily_context WHERE isDeleted = 1")
    suspend fun getTombstoneCount(): Int


    // --- LOOKUPS ---

    /**
     * Direct lookup for Sync/Repository logic.
     * Includes deleted records so we can update existing tombstones.
     */
    @Query("SELECT * FROM daily_context WHERE id = :id LIMIT 1")
    suspend fun getContextById(id: String): DailyContextEntity?

    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getContextByDay(epochDay: Long): DailyContextEntity?

    // --- UI OBSERVABLES (Filtered) ---

    /**
     * The UI Anchor:
     * Filters out tombstones so the user doesn't see "deleted" days.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay AND isDeleted = 0 LIMIT 1")
    fun observeContext(epochDay: Long): Flow<DailyContextEntity?>

    @Query("SELECT * FROM daily_context WHERE isDeleted = 0 ORDER BY epochDay DESC")
    fun observeAllContexts(): Flow<List<DailyContextEntity>>

    // --- NAP LOGIC (Filtered) ---

    @Query("SELECT * FROM daily_context WHERE isNapped = 1 AND isDeleted = 0")
    fun observeAllNappedContexts(): Flow<List<DailyContextEntity>>

    @Query("SELECT * FROM daily_context WHERE isNapped = 0 AND isDeleted = 0")
    fun observeAllNonNappedContexts(): Flow<List<DailyContextEntity>>
}