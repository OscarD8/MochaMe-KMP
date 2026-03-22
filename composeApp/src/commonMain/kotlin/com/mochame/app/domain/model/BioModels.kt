package com.mochame.app.domain.model

import com.mochame.app.domain.repository.sync.LocalFirstEntity

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    override val id: String,        // Deterministic: epochDay.toString()
    override val hlc: String = "",
    val epochDay: Long,
    val sleepHours: Double,
    val readinessScore: Int,
    val isNapped: Boolean = false,
    val isDeleted: Boolean = false, // The "Tombstone" flag
    val lastModified: Long = 0L     // Driven by hlc.ts for UI consistency
) : LocalFirstEntity<DailyContext> {

    /**
     * The Contract Implementation:
     * Returns a NEW instance with the updated HLC pulse.
     */
    override fun withHlc(hlc: String): DailyContext = copy(hlc = hlc)

    /**
     * UI Refinement:
     * Anchors the 'Human' timestamp to the 'Logical' HLC timestamp.
     */
    fun withPhysicalTime(ts: Long): DailyContext = copy(lastModified = ts)
}