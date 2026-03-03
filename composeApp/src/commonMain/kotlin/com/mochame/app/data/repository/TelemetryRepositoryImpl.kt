package com.mochame.app.data.repository

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DomainAlreadyExistsException
import com.mochame.app.core.DomainInUseException
import com.mochame.app.core.DomainNotFoundException
import com.mochame.app.core.SpaceAlreadyExistsException
import com.mochame.app.core.SpaceInUseException
import com.mochame.app.core.TopicAlreadyExistsException
import com.mochame.app.core.TopicInUseException
import com.mochame.app.core.TopicNotFoundException
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.domain.model.Domain
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.Space
import com.mochame.app.domain.model.Topic
import com.mochame.app.domain.repository.TelemetryRepository
import com.mochame.app.domain.repository.BioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
        satisfactionScore: Int,
        moodScore: Int,
        energyScore: Int,
        // Optional Enrichment
        topicId: String?,
        spaceId: String?,
        note: String?,
        isFocusTime: Boolean?,
        socialScale: Int?,
        energyDrain: Int?,
        biophiliaScale: Int?,
        durationMinutes: Int?
    ) = withContext(Dispatchers.IO) {
        val now = now().toEpochMilliseconds()

        // 1. Calculate the 'Biological Day' (The 4 AM Rule)
        val associatedDay = bioRepository.getCurrentBioDay()

        // 2. Defaulting: Space Enrichment
        // If the user didn't provide a biophilia scale, try to get it from the Space
        val finalBiophilia = if (biophiliaScale == null && spaceId != null) {
            telemetryDao.getSpaceById(spaceId)?.defaultBiophilia
        } else {
            biophiliaScale
        }

        // 3. Assemble the Molecule
        val newMoment = Moment(
            id = uuid4().toString(),
            domainId = domainId,

            // The Pulse
            satisfactionScore = satisfactionScore,
            moodScore = moodScore,
            energyScore = energyScore,

            // The Enrichment
            topicId = topicId,
            spaceId = spaceId,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            isFocusTime = isFocusTime,
            socialScale = socialScale,
            energyDrain = energyDrain,
            biophiliaScale = finalBiophilia,
            durationMinutes = durationMinutes,

            // Weather Context (Placeholders for WorkManager to fill later)
            isDaylight = null,
            cloudDensity = null,
            isPrecipitating = null,

            // Metadata
            timestamp = now,
            associatedEpochDay = associatedDay,
            lastModified = now
        )

        // 4. Persistence
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
        iconKey: String, // The new semantic anchor
        isActive: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        // 1. The Lock: Prevents naming collisions across concurrent threads
        domainMutex.withLock {

            // 2. The Check: Using lowercase to ensure "Work" and "work" don't coexist
            val existing = telemetryDao.getDomainByName(cleanName.lowercase())

            if (existing != null) {
                // 3. The Block: Explicit error reporting for the UI to handle
                throw DomainAlreadyExistsException(cleanName)
            }

            // 4. The Creation: Mapping to the Domain molecule first
            val newDomain = Domain(
                id = uuid4().toString(),
                name = cleanName,
                hexColor = hexColor,
                iconKey = iconKey, // Anchoring the icon
                isActive = isActive,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )

            // 5. The Persistence: Converting to the SQLite Entity for storage
            telemetryDao.upsertDomain(newDomain.toEntity())
        }
    }

    override fun getDomainByIdFlow(id: String): Flow<Domain?> =
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
        domainId: String,
        isActive: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        topicMutex.withLock {
            // We check if this name exists ALREADY in this specific domain
            val existing = telemetryDao.getTopicByNameInDomain(cleanName.lowercase(), domainId)

            if (existing != null) {
                throw TopicAlreadyExistsException(cleanName, domainId)
            }

            val newTopic = Topic(
                id = uuid4().toString(),
                domainId = domainId,
                name = cleanName,
                isActive = isActive,
                lastModified = now().toEpochMilliseconds()
            )

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

    override fun getTopic(topicId: String): Flow<Topic?> {
        return telemetryDao.getTopicByIdFlow(topicId).map { it?.toDomain() }
    }


    // --- SPACES ---
    private val spaceMutex = Mutex()

    override fun getActiveSpaces(): Flow<List<Space>> =
        telemetryDao.getActiveSpacesFlow()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun getSpaceById(id: String): Space? =
        telemetryDao.getSpaceById(id)?.toDomain()

    override suspend fun logSpace(
        name: String,
        iconKey: String,
        defaultBiophilia: Int?,
        isControlled: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()

        spaceMutex.withLock {
            // 1. The Identity Guard: Check for semantic duplication
            val existing = telemetryDao.getSpaceByName(cleanName.lowercase())

            if (existing != null) {
                // 2. The Block: Report the collision
                throw SpaceAlreadyExistsException(cleanName)
            }

            // 3. The Creation: Proceed only if the path is clear
            val now = now().toEpochMilliseconds()

            val newSpace = Space(
                id = uuid4().toString(),
                name = cleanName,
                iconKey = iconKey,
                defaultBiophilia = defaultBiophilia,
                isControlled = isControlled,
                isActive = true,
                lastModified = now
            )

            telemetryDao.upsertSpace(newSpace.toEntity())
        }
    }

    override suspend fun deleteSpace(id: String) = withContext(Dispatchers.IO) {
        // 1. Audit the Space's history
        val usageCount = telemetryDao.getMomentCountForSpace(id)

        if (usageCount > 0) {
            // 2. Raise the Shield
            throw SpaceInUseException(id, usageCount)
        } else {
            // 3. Perform the excision
            telemetryDao.deleteSpaceById(id)
        }
    }

    override suspend fun upsertSpace(space: Space) = withContext(Dispatchers.IO) {
        // We update the timestamp so the "Brain" (ok Gemini) knows this data is fresh
        val updatedSpace = space.copy(lastModified = now().toEpochMilliseconds())

        telemetryDao.upsertSpace(updatedSpace.toEntity())
    }

}