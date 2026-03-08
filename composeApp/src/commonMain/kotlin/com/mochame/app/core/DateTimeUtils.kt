package com.mochame.app.core

import kotlinx.datetime.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant


class DateTimeUtils {

    /**
     * Returns the current system instant.
     */
    fun now(): Instant = Clock.System.now()

    /**
     * The Biological Anchor logic.
     * Subtracts 4 hours from the actual time to determine which "Biological Day"
     * the moment belongs to.
     * * Example:
     * Jan 2nd, 02:00 AM -> (minus 4h) -> Jan 1st, 10:00 PM. Result: Jan 1st epoch day.
     */
    fun calculateBiologicalEpochDay(instant: Instant): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val biologicalInstant = instant.minus(4, DateTimeUnit.HOUR)
        return biologicalInstant.toLocalDateTime(timeZone).date.toEpochDays()
    }

    /**
     * Converts an Epoch Day back to a formatted string for UI display.
     * (e.g., "Monday, Oct 12")
     */
    fun formatEpochDay(epochDay: Long): String {
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        // In KMP, custom formatting is often handled via a simple manual mapper
        // or a library like 'kotlinx-datetime-format' in newer versions.
        return "${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${date.month.name.take(3)} ${date.day}"
    }

    /**
     * Determines if the current time is roughly 'Daylight' for the telemetry log.
     * This is a "Calm-Tech" heuristic—avoiding a heavy GPS-based API for a simple utility.
     */
    fun isDaylight(instant: Instant): Boolean {
        val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return hour in 6..19 // 6 AM to 7:59 PM
    }
}