package com.mochame.app.domain.repository

import com.mochame.app.domain.model.Domain
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.Space
import com.mochame.app.domain.model.Topic
import kotlinx.coroutines.flow.Flow

/**
 * The Domain contract for the Telemetry module.
 * * This repository manages the Moments and the Categories/Topics.
 * It is designed to be platform-agnostic and relies on the Bridge implementation
 * to handle Room-specific entities and the biological "Midnight Rule" logic.
 */
interface TelemetryRepository {

    // --- MOMENTS ---
    /**
     * Streams moments for a specific biological day.
     * @param epochDay The day index calculated using the 4:00 AM biological rollover.
     */
    fun getMomentsByDay(epochDay: Long): Flow<List<Moment>>

    /**
     * Persists or updates a Moment.
     * Implementation must handle metadata enrichment (lastModified and associatedEpochDay).
     */
    suspend fun saveMoment(moment: Moment)

    suspend fun logMoment(
        domainId: String,
        satisfactionScore: Int,
        moodScore: Int,
        energyScore: Int,
        // Optional Enrichment
        topicId: String? = null,
        spaceId: String? = null,
        note: String? = null,
        isFocusTime: Boolean? = null,
        socialScale: Int? = null,
        energyDrain: Int? = null,
        biophiliaScale: Int? = null,
        durationMinutes: Int? = null
    )

    /**
     * Atomic removal of a moment via its unique identifier.
     */
    suspend fun deleteMoment(momentId: String)


    // --- DOMAINS ---
    /**
     * The Foundation Constructor:
     * Creates a new Domain.
     * hexColor defaults to a neutral 'Mocha' (#8D775F) if not specified.
     */
    suspend fun logDomain(
        name: String,
        hexColor: String = "#8D775F",
        iconKey: String,
        isActive: Boolean = true
    )

    /**
     * Streams all active categories for the UI taxonomy picker.
     */
    fun getActiveDomains(): Flow<List<Domain>>

    /**
     * Streams all inactive categories for the UI taxonomy picker.
     */
    fun getInactiveDomains(): Flow<List<Domain>>

    /**
     * Persists or updates a Domain and refreshes its sync heartbeat.
     */
    suspend fun upsertDomain(domain: Domain)

    /**
     * Removes a Domain.
     * Note: Underlying persistence should handle cascading deletes for orphaned moments/topics.
     */
    suspend fun deleteDomain(domainId: String)

    /**
     * Updates the status on a Domain to inactive.
     * Useful in cases where a Domain has linked data.
     */
    suspend fun archiveDomain(domainId: String)

    /**
     * Streams a specific Domain for observation or editing.
     * Emits null if the Domain is deleted during observation.
     */
    fun getDomainByIdFlow(id: String): Flow<Domain?>


    // --- TOPICS ---
    suspend fun logTopic(
        name: String,
        domainId: String,
        isActive: Boolean = true
    )

    /**
     * Streams a specific Topic for observation or editing.
     * Emits null if the Topic is deleted during observation.
     */
    fun getTopic(topicId: String): Flow<Topic?>

    /**
     * Streams active topics associated with a parent Domain.
     */
    fun getAllTopicsByDomain(domainId: String): Flow<List<Topic>>

    /**
     * Updates an existing topic's details (name, parent, etc.)
     */
    suspend fun upsertTopic(topic: Topic)

    /**
     * Attempts a hard delete. Throws TopicInUseException if moments exist.
     */
    suspend fun deleteTopic(topicId: String)

    /**
     * Archives a topic by setting isActive = false.
     */
    suspend fun archiveTopic(topicId: String)

    // --- SPACES ---
    fun getActiveSpaces(): Flow<List<Space>>

    suspend fun getSpaceById(id: String): Space?

    suspend fun logSpace(
        name: String,
        iconKey: String,
        defaultBiophilia: Int?,
        isControlled: Boolean
    )

    suspend fun deleteSpace(id: String)

    suspend fun upsertSpace(space: Space)
}