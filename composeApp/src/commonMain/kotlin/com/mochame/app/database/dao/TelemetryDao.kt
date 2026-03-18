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

//    ================================================================================
//    MOMENTS
//    ================================================================================
    @Upsert
    suspend fun upsertMoment(moment: MomentEntity)

    @Query("SELECT * FROM moments WHERE id = :id")
    suspend fun getMomentById(id: String): MomentEntity?

    @Query("SELECT * FROM moments ORDER BY timestamp DESC")
    fun observeAllMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments WHERE associatedEpochDay = :epochDay ORDER BY timestamp ASC")
    fun observeMomentsByEpochDay(epochDay: Long): Flow<List<MomentEntity>>

    @Query("""
        SELECT m.* FROM moments m
        LEFT JOIN daily_context d ON m.associatedEpochDay = d.epochDay
        WHERE m.associatedEpochDay = :epochDay        
        ORDER BY m.timestamp DESC
    """)
    fun observeMomentsForDay(epochDay: Long): Flow<List<MomentEntity>>

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun deleteMomentById(id: String)

    @Query("SELECT COUNT(id) FROM moments WHERE domainId = :domainId")
    suspend fun getMomentCountForDomain(domainId: String): Int


//    ================================================================================
//    DOMAINS
//    ================================================================================
    @Upsert
    suspend fun upsertDomain(domain: DomainEntity)

    @Query("SELECT * FROM domains WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE isActive = 0 ORDER BY name ASC")
    fun observeInactiveDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getDomainByName(name: String): DomainEntity?

    @Query("SELECT * FROM domains WHERE id = :domainId LIMIT 1")
    suspend fun getDomainById(domainId: String): DomainEntity?

    @Query("DELETE FROM domains WHERE id = :domainId")
    suspend fun deleteDomainById(domainId: String)

    @Query("SELECT * FROM domains WHERE id = :domainId LIMIT 1")
    fun observeDomainById(domainId: String): Flow<DomainEntity?>


//    ================================================================================
//    TOPICS
//    ================================================================================
    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("SELECT domainId FROM topics WHERE :id = id LIMIT 1")
    suspend fun getTopicDomainId(id: String) : String

    @Query("SELECT id FROM topics WHERE :topicId = id LIMIT 1")
    suspend fun getTopicId(topicId: String) : String?

    @Query("SELECT * FROM topics WHERE name = LOWER(:name) LIMIT 1")
    suspend fun getTopicByName(name: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE domainId = :domainId AND isActive = 1")
    fun observeActiveTopicsInDomain(domainId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE domainId = :domainId AND isActive = 0")
    fun observeInactiveTopicsInDomain(domainId: String): Flow<List<TopicEntity>>

    /**
     * Finds a topic by name, but only within a specific domain.
     * This allows "Basics" to exist in 'Cooking' and 'Code' simultaneously.
     */
    @Query("SELECT * FROM topics WHERE LOWER(name) = LOWER(:name) AND domainId = :domainId LIMIT 1")
    suspend fun getTopicByNameInDomain(name: String, domainId: String): TopicEntity?
    @Query("SELECT COUNT(id) FROM moments WHERE topicId = :topicId")
    suspend fun getMomentCountForTopic(topicId: String): Int

    @Query("DELETE FROM topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: String)

    @Query("SELECT * FROM topics WHERE id = :topicId LIMIT 1")
    fun observeTopicById(topicId: String): Flow<TopicEntity?>


//    ================================================================================
//    SPACES
//    ================================================================================
    @Upsert
    suspend fun upsertSpace(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE isActive = 1")
    fun observeActiveSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE isActive = 0")
    fun observeInactiveSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getSpaceById(id: String): SpaceEntity?

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteSpaceById(id: String)

    @Query("SELECT COUNT(*) FROM moments WHERE spaceId = :spaceId")
    suspend fun getMomentCountForSpace(spaceId: String): Int

    @Query("SELECT * FROM spaces WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSpaceByName(name: String): SpaceEntity?


//    ================================================================================
//    DELETION
//    ================================================================================
    @Query("DELETE FROM domains WHERE id = :id")
    suspend fun hardDeleteDomain(id: String)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun hardDeleteTopic(id: String)

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun hardDeleteMoment(id: String)
}