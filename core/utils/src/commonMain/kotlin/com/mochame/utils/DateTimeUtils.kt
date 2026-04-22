package com.mochame.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

open class DateTimeUtils {

    /**
     * Returns the current system instant.
     */
    open fun now(): Instant = Clock.System.now()

    /**
     * THE MOCHA DAY:
     * The single source of truth for the "Current Biological Day".
     * Wraps the anchor logic using the current system time.
     */
    fun getMochaDay(): Long {
        return calculateMochaEpochDay(now())
    }

    /**
     * The Biological Anchor logic.
     * Subtracts 4 hours from the actual time to determine which "Biological Day"
     * the moment belongs to.
     */
    fun calculateMochaEpochDay(instant: Instant): Long {
        val timeZone = TimeZone.currentSystemDefault()
        // Using the more idiomatic .minus(duration) from Kotlin 1.9+
        val biologicalInstant = instant.minus(4.hours)
        return biologicalInstant.toLocalDateTime(timeZone).date.toEpochDays()
    }

    /**
     * Calculates the millisecond threshold for pruning.
     * Aligns with the start of that Mocha Day (04:00 AM).
     */
    fun getMillisForDaysAgo(days: Int): Long {
        val timeZone = TimeZone.Companion.currentSystemDefault()

        // 1. Get the target Mocha Day as a LocalDate
        val targetDate = LocalDate.Companion.fromEpochDays((getMochaDay() - days).toInt())

        // 2. idiomatic way to set the 4 AM threshold
        return targetDate.atTime(hour = 4, minute = 0)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

    /**
     * Converts an Epoch Day back to a formatted string.
     * (e.g., "Monday, Oct 12")
     */
    fun formatEpochDay(epochDay: Long): String {
        val date = LocalDate.Companion.fromEpochDays(epochDay.toInt())
        val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val monthName = date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }

        return "$dayName, $monthName ${date.day}"
    }

    /**
     * Calm-Tech Heuristic for UI/Telemetry.
     */
    fun isDaylight(instant: Instant): Boolean {
        val hour = instant.toLocalDateTime(TimeZone.Companion.currentSystemDefault()).hour
        return hour in 6..19
    }
}