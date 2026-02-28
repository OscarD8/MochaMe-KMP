package com.mochame.app.data.telemetry

import androidx.room.*
import com.mochame.app.database.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    /**
     * Observes all active categories (e.g., Coding, Reading, Deep Work).
     * This provides the "anchors" for the user to select from when logging a Moment.
     */
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    /**
     * Saves or updates a category.
     * The lastModified field (handled in the Mapper) ensures sync-readiness.
     */
    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    /**
     * Soft-delete functionality for categories to preserve historical data
     * while removing them from the user's active selection.
     */
    @Query("UPDATE categories SET isActive = 0, lastModified = :timestamp WHERE id = :id")
    suspend fun archiveCategory(id: String, timestamp: Long)
}