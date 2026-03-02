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
    fun getContextByEpochDay(epochDay: Long): Flow<DailyContextEntity?>

    /**
     * Persists or updates the context for the day.
     * The @Upsert handles the unique index on epochDay automatically.
     */
    @Upsert
    suspend fun upsertDailyContext(context: DailyContextEntity)

    /**
     * Fetches all history for daily context long-term analysis.
     */
    @Query("SELECT * FROM daily_context ORDER BY epochDay DESC")
    fun getAllContextsFlow(): Flow<List<DailyContextEntity>>

    /**
     * Atomic Deletion by ID for UI cleanup or data resets.
     */
    @Query("DELETE FROM daily_context WHERE id = :id")
    suspend fun deleteContextById(id: String)
}