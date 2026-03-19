package com.mochame.app.domain.repository.telemetry

import com.mochame.app.domain.model.telemetry.Moment
import com.mochame.app.domain.model.telemetry.MomentDraft

/**
 * Defines the high-frequency ingestion of "Moments" into the system.
 *
 * All implementations must adhere to the "4:00 AM Midnight Rule," ensuring that
 * telemetry is anchored to the user's biological day (The Cup) rather than
 * strict chronological time (The Brew).
 */
interface MomentRepository {
    /**
     * The primary entry point for capturing a biological moment.
     * Enforces the 4:00 AM Midnight Rule and handles Space-based enrichment.
     */
    suspend fun logMoment(draft: MomentDraft)

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