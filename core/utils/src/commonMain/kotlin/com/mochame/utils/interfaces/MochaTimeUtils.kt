package com.mochame.utils.interfaces

import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Domain-specific functionality on 4 AM cutoff.
 */
interface MochaTimeUtils : TimeUtils {
    fun getMochaDay(): Long

    fun calculateMochaEpochDay(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long

    override fun getMillisAgo(
        duration: Duration,
        timeZone: TimeZone
    ): Long
}