package com.mochame.app.data.repository

import com.benasher44.uuid.uuid4
import com.mochame.app.core.CategoryAlreadyExistsException
import com.mochame.app.core.CategoryInUseException
import com.mochame.app.core.CategoryNotFoundException
import com.mochame.app.core.TopicAlreadyExistsException
import com.mochame.app.core.TopicInUseException
import com.mochame.app.core.TopicNotFoundException
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.domain.model.Category
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.Topic
import com.mochame.app.domain.repository.TelemetryRepository
import com.mochame.app.domain.repository.BioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Clock.System.now

class TelemetryRepositoryImpl(
    private val telemetryDao: TelemetryDao,
    private val bioRepository: BioRepository
) : TelemetryRepository {

    // --- MOMENTS ---
    override fun getMomentsByDay(epochDay: Long): Flow<List<Moment>> {
        return telemetryDao.getMomentsByEpochDay(epochDay).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun logMoment(
        categoryId: String,
        topicId: String?,
        note: String,
        durationMinutes: Int,
        satisfactionScore: Int,
        energyScore: Int,
        moodScore: Int
    ) = withContext(Dispatchers.IO) {

        val currentBioDay = bioRepository.getCurrentBioDay()

        val newMoment = Moment(
            id = uuid4().toString(),
            timestamp = now().toEpochMilliseconds(),
            associatedEpochDay = currentBioDay,
            categoryId = categoryId,
            topicId = topicId,
            durationMinutes = durationMinutes,
            satisfactionScore = satisfactionScore,
            energyScore = energyScore,
            moodScore = moodScore,
            note = note,
            lastModified = now().toEpochMilliseconds()
        )

        telemetryDao.upsertMoment(newMoment.toEntity())
    }

    override suspend fun saveMoment(moment: Moment) = withContext(Dispatchers.IO) {
        // 2. PERSIST: We take the moment as-is, but refresh the sync heartbeat.
        // Notice we DO NOT recalculate associatedEpochDay.
        val updatedMoment = moment.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        telemetryDao.upsertMoment(updatedMoment.toEntity())
    }

    override suspend fun deleteMoment(momentId: String) = withContext(Dispatchers.IO) {
        // No dummy entities, just the ID
        telemetryDao.deleteMomentById(momentId)
    }


    // --- CATEGORIES ---
    private val categoryMutex = Mutex()

    override fun getActiveCategories(): Flow<List<Category>> {
        return telemetryDao.getActiveCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertCategory(category: Category) = withContext(Dispatchers.IO) {
        val updatedCategory = category.copy(
            lastModified = now().toEpochMilliseconds()
        )
        telemetryDao.upsertCategory(updatedCategory.toEntity())
    }

    override suspend fun deleteCategory(categoryId: String) = categoryMutex.withLock {
        withContext(Dispatchers.IO) {
            // Atomic Check-and-Delete: No moments can be added to this ID while we are checking.
            val usageCount = telemetryDao.getMomentCountForCategory(categoryId)

            if (usageCount == 0) {
                telemetryDao.deleteCategoryById(categoryId)
            } else {
                throw CategoryInUseException(usageCount)
            }
        }
    }

    // Separate function to handle the "Hide" intent
    override suspend fun archiveCategory(categoryId: String) = withContext(Dispatchers.IO) {
        val existing =
            telemetryDao.getCategoryById(categoryId) ?: throw CategoryNotFoundException(categoryId)

        val updated = existing.toDomain().copy(
            isActive = false,
            lastModified = now().toEpochMilliseconds()
        )

        telemetryDao.upsertCategory(updated.toEntity())
    }

    override suspend fun logCategory(
        name: String,
        hexColor: String,
        isActive: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        // 1. The Lock: Only one 'Creation' attempt happens at a time
        categoryMutex.withLock {
            // 2. The Check
            val existing = telemetryDao.getCategoryByName(cleanName.lowercase())

            if (existing != null) {
                // 3. The Block: We don't 'Guess' intent. We report the conflict.
                throw CategoryAlreadyExistsException(cleanName)
            }

            // 4. The Creation (Only reached if name is truly unique)
            val newCategory = Category(
                id = uuid4().toString(),
                name = cleanName,
                hexColor = hexColor,
                isActive = isActive,
                lastModified = now().toEpochMilliseconds()
            )
            telemetryDao.upsertCategory(newCategory.toEntity())
        }
    }


    // --- TOPICS ---
    private val topicMutex = Mutex()

    override fun getAllTopicsByCategory(categoryId: String): Flow<List<Topic>> {
        return telemetryDao.getTopicsByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertTopic(topic: Topic) = withContext(Dispatchers.IO) {
        val updatedTopic = topic.copy(
            lastModified = now().toEpochMilliseconds()
        )
        telemetryDao.upsertTopic(updatedTopic.toEntity())
    }

    override suspend fun logTopic(
        name: String,
        parentId: String?,
        isActive: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        // 1. Identity Guard: Prevent race conditions
        topicMutex.withLock {
            // 2. Naming Authority: Check for duplicates in the Kernel
            val existing = telemetryDao.getTopicByName(cleanName.lowercase())

            if (existing != null) {
                // 3. Conflict Reported: Let the UI decide how to handle the overlap
                throw TopicAlreadyExistsException(cleanName)
            }

            // 4. Creation: Unique Identity Assertion
            val now = now().toEpochMilliseconds()
            val newTopic = Topic(
                id = uuid4().toString(),
                parentId = parentId,
                name = cleanName,
                isActive = isActive,
                lastModified = now
            )

            // 5. Persist to database
            telemetryDao.upsertTopic(newTopic.toEntity())
        }
    }



    override suspend fun deleteTopic(topicId: String) = topicMutex.withLock {
        withContext(Dispatchers.IO) {
            val usageCount = telemetryDao.getMomentCountForTopic(topicId)

            if (usageCount == 0) {
                telemetryDao.deleteTopicById(topicId)
            } else {
                throw TopicInUseException(usageCount)
            }
        }
    }

    override suspend fun archiveTopic(topicId: String) = withContext(Dispatchers.IO) {
        val existing = telemetryDao.getTopicById(topicId) ?: throw TopicNotFoundException(topicId)

        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = now().toEpochMilliseconds()
        )

        telemetryDao.upsertTopic(archived.toEntity())
    }

}