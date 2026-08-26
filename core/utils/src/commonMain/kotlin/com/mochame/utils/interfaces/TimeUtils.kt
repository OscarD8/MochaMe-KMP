package com.mochame.utils.interfaces

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

interface TimeUtils {
    fun now(): Instant

    fun getMillisAgo(
        duration: Duration,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long = (now() - duration).toEpochMilliseconds()

    fun formatEpochDay(epochDay: Long): String {
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val monthName = date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }
        return "$dayName, $monthName ${date.day}"
    }

    fun isDaylight(instant: Instant): Boolean {
        val hour = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return hour in 6..19
    }
}