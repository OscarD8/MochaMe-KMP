package com.mochame.app.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mochame.app.database.entity.DomainEntity
import com.mochame.app.database.entity.MomentEntity
import com.mochame.app.database.entity.SpaceEntity
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

    @Query("SELECT COUNT(id) FROM moments WHERE domainId = :domainId")
    suspend fun getMomentCountForDomain(domainId: String): Int


    // --- DOMAINS ---
    @Upsert
    suspend fun upsertDomain(domain: DomainEntity)

    @Query("SELECT * FROM domains WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE isActive = 0 ORDER BY name ASC")
    fun getInactiveDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE name = :name LIMIT 1")
    suspend fun getDomainByName(name: String): DomainEntity?

    @Query("SELECT * FROM domains WHERE id = :domainId LIMIT 1")
    suspend fun getDomainById(domainId: String): DomainEntity?

    @Query("DELETE FROM domains WHERE id = :domainId")
    suspend fun deleteDomainById(domainId: String)

    @Query("SELECT * FROM domains WHERE id = :id LIMIT 1")
    fun getDomainByIdFlow(id: String): Flow<DomainEntity?>

    // --- TOPICS ---
    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("SELECT * FROM topics WHERE name = :name LIMIT 1")
    suspend fun getTopicByName(name: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE parentId = :domainId AND isActive = 1")
    fun getTopicsByDomain(domainId: String): Flow<List<TopicEntity>>

    @Query("SELECT COUNT(id) FROM moments WHERE topicId = :topicId")
    suspend fun getMomentCountForTopic(topicId: String): Int

    @Query("DELETE FROM topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: String)


    // SPACES
    @Upsert
    suspend fun upsertSpace(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE isActive = 1")
    fun getActiveSpacesFlow(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getSpaceById(id: String): SpaceEntity?

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteSpaceById(id: String)

    @Query("SELECT COUNT(*) FROM moments WHERE spaceId = :spaceId")
    suspend fun getMomentCountForSpace(spaceId: String): Int

}