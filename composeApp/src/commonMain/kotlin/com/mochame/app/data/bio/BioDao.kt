package com.mochame.app.data.bio

import androidx.room.*
import com.mochame.app.database.entities.DailyContextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BioDao {
    /**
     * Retrieves the biological context for a specific day.
     * Uses Flow to reactively update the UI when sleep hours are adjusted.
     */
    @Query("SELECT * FROM daily_context WHERE epochDay = :epochDay LIMIT 1")
    fun getContextForDay(epochDay: Long): Flow<DailyContextEntity?>

    /**
     * Anchors the day's biological capacity.
     * Upsert handles the "Initialize Context" user story.
     */
    @Upsert
    suspend fun upsertContext(context: DailyContextEntity)
}