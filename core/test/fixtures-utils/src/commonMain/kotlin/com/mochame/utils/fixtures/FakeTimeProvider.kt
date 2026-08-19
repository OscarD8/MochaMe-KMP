package com.mochame.utils.fixtures

import com.mochame.sync.api.hlc.HLC
import com.mochame.utils.interfaces.MochaTimeProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Defaults initial time to: Saturday, March 1, 2025 at 00:00:00 UTC.
 */
class FakeTimeProvider(
    initialTime: Instant = HLC.APP_RELEASE_TIME.plus(1.days),
    private var defaultTimeZone: TimeZone = TimeZone.currentSystemDefault()
) : MochaTimeProvider {

    private val lock = reentrantLock()
    private var currentTime: Instant = initialTime

    fun advanceTime(duration: Duration) = lock.withLock { currentTime += duration }
    fun reverseTime(duration: Duration) = lock.withLock { currentTime -= duration }
    fun setTime(instant: Instant) = lock.withLock { currentTime = instant }
    fun setTimeZone(timeZone: TimeZone) = lock.withLock { defaultTimeZone = timeZone }

    override fun now(): Instant = lock.withLock { currentTime }

    override fun getMochaDay(): Long {
        return calculateMochaEpochDay(now())
    }

    override fun calculateMochaEpochDay(instant: Instant): Long {
        val tz = lock.withLock { defaultTimeZone }
        val biologicalInstant = instant.minus(4.hours)
        return biologicalInstant.toLocalDateTime(tz).date.toEpochDays().toLong()
    }

    override fun getMillisAgo(duration: Duration): Long {
        val tz = lock.withLock { defaultTimeZone }
        val targetMochaDay = getMochaDay() - duration.inWholeDays
        val targetDate = LocalDate.fromEpochDays(targetMochaDay.toInt())

        return targetDate.atTime(hour = 4, minute = 0)
            .toInstant(tz)
            .toEpochMilliseconds()
    }
}