package com.mochame.app.domain.repository

import com.mochame.app.domain.model.Category
import com.mochame.app.domain.model.Moment
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
        categoryId: String,
        topicId: String?,
        note: String = "",
        durationMinutes: Int = 0,
        satisfactionScore: Int = 0,
        energyScore: Int = 0,
        moodScore: Int = 0
    )

    /**
     * Atomic removal of a moment via its unique identifier.
     */
    suspend fun deleteMoment(momentId: String)


    // --- CATEGORIES ---
    /**
     * The Foundation Constructor:
     * Creates a new activity category.
     * hexColor defaults to a neutral 'Mocha' (#8D775F) if not specified.
     */
    suspend fun logCategory(
        name: String,
        hexColor: String = "#8D775F",
        isActive: Boolean = true
    )

    /**
     * Streams all active categories for the UI taxonomy picker.
     */
    fun getActiveCategories(): Flow<List<Category>>

    /**
     * Persists or updates a Category and refreshes its sync heartbeat.
     */
    suspend fun upsertCategory(category: Category)

    /**
     * Removes a category.
     * Note: Underlying persistence should handle cascading deletes for orphaned moments/topics.
     */
    suspend fun deleteCategory(categoryId: String)

    /**
     * Updates the status on a category to inactive.
     * Useful in cases where a category has linked data.
     */
    suspend fun archiveCategory(categoryId: String)


    // --- TOPICS ---
    suspend fun logTopic(
        name: String,
        parentId: String? = null,
        isActive: Boolean = true
    )

    /**
     * Streams active topics associated with a parent category.
     */
    fun getAllTopicsByCategory(categoryId: String): Flow<List<Topic>>

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
}