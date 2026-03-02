package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.database.entity.CategoryEntity
import com.mochame.app.database.entity.MomentEntity
import com.mochame.app.database.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    // --- MOMENTS ---
    @Upsert
    suspend fun upsertMoment(moment: MomentEntity)

    @Query("SELECT * FROM moments WHERE id = :id")
    suspend fun getMomentById(id: String): MomentEntity?

    @Query("SELECT * FROM moments ORDER BY timestamp DESC")
    fun getAllMomentsFlow(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments WHERE associatedEpochDay = :epochDay ORDER BY timestamp ASC")
    fun getMomentsByEpochDay(epochDay: Long): Flow<List<MomentEntity>>

    @Query("""
        SELECT * FROM moments 
        WHERE associatedEpochDay IN (SELECT epochDay FROM daily_context)
        ORDER BY timestamp DESC
    """)
    fun getMomentsWithBioContext(): Flow<List<MomentEntity>>

    /**
     * Atomic deletion using the Primary Key.
     * This removes the need to construct a dummy MomentEntity in the repository layer.
     */
    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun deleteMomentById(id: String)

    @Query("SELECT COUNT(id) FROM moments WHERE categoryId = :categoryId")
    suspend fun getMomentCountForCategory(categoryId: String): Int


    // --- CATEGORIES ---
    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)


    // --- TOPICS ---
    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("SELECT * FROM topics WHERE name = :name LIMIT 1")
    suspend fun getTopicByName(name: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE parentId = :categoryId AND isActive = 1")
    fun getTopicsByCategory(categoryId: String): Flow<List<TopicEntity>>

    @Query("SELECT COUNT(id) FROM moments WHERE topicId = :topicId")
    suspend fun getMomentCountForTopic(topicId: String): Int

    @Query("DELETE FROM topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: String)

}