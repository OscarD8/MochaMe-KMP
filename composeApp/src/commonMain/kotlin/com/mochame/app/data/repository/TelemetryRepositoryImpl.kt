package com.mochame.app.data.repository

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DomainAlreadyExistsException
import com.mochame.app.core.DomainInUseException
import com.mochame.app.core.DomainNotFoundException
import com.mochame.app.core.DomainInUseException
import com.mochame.app.core.DomainNotFoundException
import com.mochame.app.core.TopicAlreadyExistsException
import com.mochame.app.core.TopicInUseException
import com.mochame.app.core.TopicNotFoundException
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.domain.model.Domain
import com.mochame.app.domain.model.Domain
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
        domainId: String,
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
            domainId = domainId,
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


    // --- DOMAINS ---
    private val domainMutex = Mutex()

    override fun getActiveDomains(): Flow<List<Domain>> {
        return telemetryDao.getActiveDomains().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getInactiveDomains(): Flow<List<Domain>> {
        return telemetryDao.getInactiveDomains().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertDomain(domain: Domain) = withContext(Dispatchers.IO) {
        val updatedDomain = domain.copy(
            lastModified = now().toEpochMilliseconds()
        )
        telemetryDao.upsertDomain(updatedDomain.toEntity())
    }

    override suspend fun deleteDomain(domainId: String) = domainMutex.withLock {
        withContext(Dispatchers.IO) {
            // Atomic Check-and-Delete: No moments can be added to this ID while we are checking.
            val usageCount = telemetryDao.getMomentCountForDomain(domainId)

            if (usageCount == 0) {
                telemetryDao.deleteDomainById(domainId)
            } else {
                throw DomainInUseException(usageCount)
            }
        }
    }

    // Separate function to handle the "Hide" intent
    override suspend fun archiveDomain(domainId: String) = withContext(Dispatchers.IO) {
        val existing =
            telemetryDao.getDomainById(domainId) ?: throw DomainNotFoundException(domainId)

        val updated = existing.toDomain().copy(
            isActive = false,
            lastModified = now().toEpochMilliseconds()
        )

        telemetryDao.upsertDomain(updated.toEntity())
    }

    override suspend fun logDomain(
        name: String,
        hexColor: String,
        isActive: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        // 1. The Lock: Only one 'Creation' attempt happens at a time
        domainMutex.withLock {
            // 2. The Check
            val existing = telemetryDao.getDomainByName(cleanName.lowercase())

            if (existing != null) {
                // 3. The Block: We don't 'Guess' intent. We report the conflict.
                throw DomainAlreadyExistsException(cleanName)
            }

            // 4. The Creation (Only reached if name is truly unique)
            val newDomain = Domain(
                id = uuid4().toString(),
                name = cleanName,
                hexColor = hexColor,
                isActive = isActive,
                lastModified = now().toEpochMilliseconds()
            )
            telemetryDao.upsertDomain(newDomain.toEntity())
        }
    }

    override fun getDomain(id: String): Flow<Domain?> =
        telemetryDao.getDomainByIdFlow(id).map { it?.toDomain() }


    // --- TOPICS ---
    private val topicMutex = Mutex()

    override fun getAllTopicsByDomain(domainId: String): Flow<List<Topic>> {
        return telemetryDao.getTopicsByDomain(domainId).map { entities ->
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