package com.mochame.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant


private val SyncTimestampFormatter = LocalDateTime.Format {
    day(padding = Padding.ZERO)
    char('/')
    monthNumber(Padding.ZERO)
    char('/')
    yearTwoDigits(baseYear = 2026)
    char(' ')
    hour(Padding.ZERO)
    char(':')
    minute(Padding.ZERO)
    char(':')
    second(Padding.ZERO)
}

/**
 * Formats epoch milliseconds into a human-readable string: "DD/MM/YY HH:MM:SS"
 * Safe for commonMain, Kotlin/Native (Linux, iOS, Mac), and JVM targets.
 */
fun Long.toDateTime(timeZone: TimeZone = TimeZone.UTC): String {
    val instant = Instant.fromEpochMilliseconds(this)

    val localDateTime = instant.toLocalDateTime(timeZone)

    return localDateTime.format(SyncTimestampFormatter)
}