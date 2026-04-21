package com.mochame.app.data.local.room.feature.telemetry.bridge

import com.benasher44.uuid.uuid4
import com.mochame.app.data.local.room.dao.TelemetryDao
import com.mochame.app.data.mappers.toDomain
import com.mochame.app.data.mappers.toEntity
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.feature.telemetry.Domain
import com.mochame.app.domain.feature.telemetry.Space
import com.mochame.app.domain.feature.telemetry.Topic
import com.mochame.app.domain.feature.telemetry.repositories.ContextRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock


/**
 * IdentityBridge: SQLite-backed implementation of [ContextRepository].
 *
 * This bridge utilizes [TelemetryDao] to enforce structural constraints at the
 * database level.
 */
internal class ContextBridge(
    private val telemetryDao: TelemetryDao,
    private val dispatcher: DispatcherProvider
) : ContextRepository {


    // --- DOMAIN ---
    private val domainMutex = Mutex()

    override suspend fun logDomain(
        name: String,
        hexColor: String,
        iconKey: String, // The new semantic anchor
        isActive: Boolean
    ) = withContext(dispatcher.io) {
        val cleanName = name.trim()

        // 1. The Lock: Prevents naming collisions across concurrent threads
        domainMutex.withLock {

            // 2. The Check: Using lowercase to ensure "Work" and "work" don't coexist
            val existing = telemetryDao.getDomainByName(cleanName.lowercase())

            if (existing != null) {
                // 3. The Block: Explicit error reporting for the UI to handle
                throw MochaException.SemanticException.Domain.AlreadyExists(existing.name, existing.id)
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

    override suspend fun upsertDomain(domain: Domain) = withContext(dispatcher.io) {
        val updatedDomain = domain.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        telemetryDao.upsertDomain(updatedDomain.toEntity())
    }

    override suspend fun deleteDomain(domainId: String) = domainMutex.withLock {
        withContext(dispatcher.io) {
            // Atomic Check-and-Delete: No moments can be added to this ID while we are checking.
            val usageCount = telemetryDao.getMomentCountForDomain(domainId)

            if (usageCount == 0) {
                telemetryDao.deleteDomainById(domainId)
            } else {
                throw MochaException.SemanticException.Domain.InUse(domainId, usageCount)
            }
        }
    }

    override suspend fun archiveDomain(domainId: String) = withContext(dispatcher.io) {
        val existing =
            telemetryDao.getDomainById(domainId) ?: throw MochaException.SemanticException.Domain.NotFound(domainId)

        val updated = existing.toDomain().copy(
            isActive = false,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )

        telemetryDao.upsertDomain(updated.toEntity())
    }


    // --- TOPIC ---
    private val topicMutex = Mutex()

    override suspend fun logTopic(
        name: String,
        domainId: String,
        isActive: Boolean
    ) = withContext(dispatcher.io) {
        val cleanName = name.trim()

        topicMutex.withLock {
            // We check if this name exists ALREADY in this specific domain
            val existing = telemetryDao.getTopicByNameInDomain(cleanName.lowercase(), domainId)

            if (existing != null) {
                throw MochaException.SemanticException.Topic.AlreadyExists(cleanName, domainId)
            }

            val newTopic = Topic(
                id = uuid4().toString(),
                domainId = domainId,
                name = cleanName,
                isActive = isActive,
                lastModified = Clock.System.now().toEpochMilliseconds()
            )

            telemetryDao.upsertTopic(newTopic.toEntity())
        }
    }

    override suspend fun upsertTopic(topic: Topic) = withContext(dispatcher.io) {
        val updatedTopic = topic.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        telemetryDao.upsertTopic(updatedTopic.toEntity())
    }

    override suspend fun deleteTopic(topicId: String) = topicMutex.withLock {
        withContext(dispatcher.io) {
            val usageCount = telemetryDao.getMomentCountForTopic(topicId)

            if (usageCount == 0) {
                telemetryDao.deleteTopicById(topicId)
            } else {
                throw MochaException.SemanticException.Topic.InUse(topicId, usageCount)
            }
        }
    }

    override suspend fun archiveTopic(topicId: String) = withContext(dispatcher.io) {
        val existing = telemetryDao.getTopicById(topicId)
            ?: throw MochaException.SemanticException.Topic.NotFound(topicId)

        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )

        telemetryDao.upsertTopic(archived.toEntity())
    }


    // --- SPACE ---
    private val spaceMutex = Mutex()

    override suspend fun logSpace(
        name: String,
        iconKey: String,
        defaultBiophilia: Int?,
        isControlled: Boolean
    ) = withContext(dispatcher.io) {
        val cleanName = name.trim()

        spaceMutex.withLock {
            // 1. The Identity Guard: Check for semantic duplication
            val existing = telemetryDao.getSpaceByName(cleanName.lowercase())

            if (existing != null) {
                // 2. The Block: Report the collision
                throw MochaException.SemanticException.Space.AlreadyExists(cleanName)
            }

            // 3. The Creation: Proceed only if the path is clear
            val now = Clock.System.now().toEpochMilliseconds()

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

    override suspend fun upsertSpace(space: Space) = withContext(dispatcher.io) {
        // We update the timestamp so the "Brain" (ok Gemini) knows this data is fresh
        val updatedSpace = space.copy(lastModified = Clock.System.now().toEpochMilliseconds())

        telemetryDao.upsertSpace(updatedSpace.toEntity())
    }

    override suspend fun deleteSpace(id: String) = withContext(dispatcher.io) {
        // 1. Audit the Space's history
        val usageCount = telemetryDao.getMomentCountForSpace(id)

        if (usageCount > 0) {
            // 2. Raise the Shield
            throw MochaException.SemanticException.Space.InUse(id, usageCount)
        } else {
            // 3. Perform the excision
            telemetryDao.deleteSpaceById(id)
        }
    }

    override suspend fun archiveSpace(id: String) = withContext(dispatcher.io) {
        // 1. The Fetch: Retrieve the entity from the DAO
        // Note: Ensure your SpaceNotFoundException is defined in your CustomExceptions.kt
        val existing = telemetryDao.getSpaceById(id) ?: throw MochaException.SemanticException.Space.NotFound(id)

        // 2. The Mutation: Convert to Domain molecule and flip the isActive bit
        // Using the .toDomain() extension function from TelemetryMappers.kt
        val archived = existing.toDomain().copy(
            isActive = false,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )

        // 3. The Persistence: Map back to Entity and save
        telemetryDao.upsertSpace(archived.toEntity())
    }

}



