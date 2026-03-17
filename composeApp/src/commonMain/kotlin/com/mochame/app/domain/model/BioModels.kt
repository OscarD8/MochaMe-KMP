package com.mochame.app.domain.model

/**
 * Represents the biological capacity and readiness for a single day.
 * This acts as the framing context for all telemetry logged during this period.
 */
data class DailyContext(
    val epochDay: Long, // Unique anchor for the biological day
    val sleepHours: Double, // The primary "fuel" metric
    val readinessScore: Int, // Qualitative metric
    val lastModified: Long // Timestamp for conflict resolution
)
