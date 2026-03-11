package com.mochame.app.database.dao

import androidx.room.*
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BioDao {
    /**
     * The "Initialization Interceptor" Query:
     * Fetches the biological context for a specific day.
     * Returns null if the user hasn't initialized their day yet.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    fun observeContextByDay(epochDay: Long): Flow<DailyContextEntity?>

    /**
     * Persists or updates the context for the day.
     * The @Insert handles the unique index on epochDay automatically.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(context: DailyContextEntity)

    /**
     * Fetches all history for daily context long-term analysis as a flow.
     */
    @Query("SELECT * FROM daily_context ORDER BY epochDay DESC")
    fun observeAllContexts(): Flow<List<DailyContextEntity>>

    /**
     * Fetches all history for daily context long-term analysis as a list.
     */
    @Query("SELECT * FROM daily_context ORDER BY epochDay DESC")
    suspend fun getAllContexts(): List<DailyContextEntity>

    /**
     * Atomic Deletion by ID for UI cleanup or data resets.
     */
    @Query("DELETE FROM daily_context WHERE id = :id")
    suspend fun deleteContextById(id: String)

    /**
     * One-shot retrieval for the "Check-and-Act" initialization pattern.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getContextByDay(epochDay: Long): DailyContextEntity?

    @Query("SELECT * FROM daily_context WHERE id = :id")
    suspend fun getById(id: String): DailyContextEntity?

    // NAPS
    // --- STATIC LISTS (One-shot) ---

    @Query("SELECT * FROM daily_context WHERE isNapped = 1")
    suspend fun getAllNappedContexts(): List<DailyContextEntity>

    @Query("SELECT * FROM daily_context WHERE isNapped = 0")
    suspend fun getAllNonNappedContexts(): List<DailyContextEntity>

    // --- FLOWS (Reactive Streams) ---

    @Query("SELECT * FROM daily_context WHERE isNapped = 1")
    fun observeAllNappedContexts(): Flow<List<DailyContextEntity>>

    @Query("SELECT * FROM daily_context WHERE isNapped = 0")
    fun observeAllNonNappedContexts(): Flow<List<DailyContextEntity>>
}