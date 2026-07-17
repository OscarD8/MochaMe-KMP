package com.mochame.utils.implementations

import com.mochame.utils.interfaces.DateTimeProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Single(binds = [DateTimeProvider::class])
open class MochaDateTimeProvider : DateTimeProvider {

    /**
     * Returns the current system instant.
     */
    override fun now(): Instant = Clock.System.now()

    /**
     * Using the current system time.
     */
    override fun getMochaDay(): Long {
        return calculateMochaEpochDay(now())
    }

    /**
     * Subtracts 4 hours from the actual time to determine which 'day'
     * the moment belongs to.
     */
    override fun calculateMochaEpochDay(instant: Instant): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val biologicalInstant = instant.minus(4.hours)
        return biologicalInstant.toLocalDateTime(timeZone).date.toEpochDays()
    }

    /**
     * Calculates the millisecond threshold for pruning.
     */
    override fun getMillisForDaysAgo(days: Int): Long {
        val timeZone = TimeZone.currentSystemDefault()

        val targetDate = LocalDate.fromEpochDays((getMochaDay() - days).toInt())

        return targetDate.atTime(hour = 4, minute = 0)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

    /**
     * Converts an Epoch Day back to a formatted string.
     * (e.g., "Monday, Oct 12")
     */
    override fun formatEpochDay(epochDay: Long): String {
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val monthName = date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }

        return "$dayName, $monthName ${date.day}"
    }

    /**
     * For UI/Telemetry.
     */
    override fun isDaylight(instant: Instant): Boolean {
        val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return hour in 6..19
    }
}

