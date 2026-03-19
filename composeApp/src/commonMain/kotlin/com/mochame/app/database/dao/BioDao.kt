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
     * Persists or updates the context for the day.
     * Room's @Upsert will handle the conflict by updating existing rows.
     * For sync, we often want "Last-Write-Wins" based on timestamps.
     */
    @Upsert
    suspend fun upsert(context: DailyContextEntity)

    /**
     * The Sync-Safe Resolver:
     * Only updates the local database if the incoming data is newer.
     * This prevents an older "Cloud" record from overwriting a newer "Local" change.
     */
    @Transaction
    suspend fun resolveSync(incoming: DailyContextEntity) {
        val localRecord = getContextByDay(incoming.epochDay)

        if (localRecord == null) {
            upsert(incoming)
        } else if (incoming.lastModified > localRecord.lastModified) {
            // we don't break local FK constraints on Moments
            upsert(incoming.copy(id = localRecord.id))
        }
    }

    /**
     * One-shot retrieval for the context. Can return null.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getContextByDay(epochDay: Long): DailyContextEntity?

    /**
     * One-shot retrieval for the context. Can return null.
     */
    @Query("SELECT * FROM daily_context WHERE id = :id LIMIT 1")
    suspend fun getContextById(id: String): DailyContextEntity?

    /**
     * The "Initialization Interceptor" Query:
     * Fetches the biological context for a specific day.
     * Returns null if the user hasn't initialized their day yet.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    fun observeContext(epochDay: Long): Flow<DailyContextEntity?>

    /**
     * Fetches all history for daily context long-term analysis as a list.
     */
    @Query("SELECT * FROM daily_context ORDER BY epochDay DESC")
    suspend fun getAllContexts(): List<DailyContextEntity>

    /**
     * Fetches all history for daily context long-term analysis as a flow.
     */
    @Query("SELECT * FROM daily_context ORDER BY epochDay DESC")
    fun observeAllContexts(): Flow<List<DailyContextEntity>>

    /**
     * Atomic Deletion by ID for UI cleanup or data resets.
     */
    @Query("DELETE FROM daily_context WHERE epochDay = :epochDay")
    suspend fun deleteContext(epochDay: Long)


    // NAP
    @Query("SELECT * FROM daily_context WHERE isNapped = 1")
    suspend fun getAllNappedContexts(): List<DailyContextEntity>

    @Query("SELECT * FROM daily_context WHERE isNapped = 0")
    suspend fun getAllNonNappedContexts(): List<DailyContextEntity>

    @Query("SELECT * FROM daily_context WHERE isNapped = 1")
    fun observeAllNappedContexts(): Flow<List<DailyContextEntity>>

    @Query("SELECT * FROM daily_context WHERE isNapped = 0")
    fun observeAllNonNappedContexts(): Flow<List<DailyContextEntity>>


    // SYNC
    override suspend fun lockForSync(ids: List<String>, sessionId: String) {
        // Move from PENDING (1) to SYNCING (2) and tag with ID
        bioDao.updateSyncStatus(ids, oldStatus = 1, newStatus = 2, sessionId = sessionId)
    }
}