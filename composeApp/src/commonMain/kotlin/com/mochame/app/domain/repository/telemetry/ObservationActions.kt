package com.mochame.app.domain.repository.telemetry

import com.mochame.app.domain.model.Moment

/**
 * Defines the high-frequency ingestion of "Moments" into the system.
 *
 * All implementations must adhere to the "4:00 AM Midnight Rule," ensuring that
 * telemetry is anchored to the user's biological day (The Cup) rather than
 * strict chronological time (The Brew).
 */
interface ObservationActions {
    /**
     * The primary entry point for capturing a biological moment.
     * Enforces the 4:00 AM Midnight Rule and handles Space-based enrichment.
     */
    suspend fun logMoment(
        domainId: String,
        satisfactionScore: Int,
        moodScore: Int,
        energyScore: Int,
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
     * Persists an existing Moment molecule, typically used for updates.
     * Refreshes the lastModified sync heartbeat.
     */
    suspend fun saveMoment(moment: Moment)

    /**
     * Atomic removal of a moment by ID.
     */
    suspend fun deleteMoment(momentId: String)
}