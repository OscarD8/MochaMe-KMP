package com.mochame.utils.fixtures

import com.mochame.sync.api.hlc.HLC
import com.mochame.utils.interfaces.MochaTimeUtils
import com.mochame.utils.interfaces.TimeUtils
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
open class FakeTimeUtils(
    initialTime: Instant = HLC.APP_RELEASE_TIME.plus(1.days)
) : TimeUtils {

    private val lock = reentrantLock()
    private var currentTime: Instant = initialTime

    fun advanceTime(duration: Duration) = lock.withLock { currentTime += duration }
    fun reverseTime(duration: Duration) = lock.withLock { currentTime -= duration }
    fun setTime(instant: Instant) = lock.withLock { currentTime = instant }

    override fun now(): Instant = lock.withLock { currentTime }
}

class MochaFakeTimeUtils(
    val baseClock: FakeTimeUtils,
    defaultTimeZone: TimeZone = TimeZone.currentSystemDefault()
) : MochaTimeUtils, TimeUtils by baseClock {

    private val lock = reentrantLock()
    private var currentTimeZone: TimeZone = defaultTimeZone

    fun setTimeZone(timeZone: TimeZone) = lock.withLock { currentTimeZone = timeZone }

    fun advanceTime(duration: Duration) = baseClock.advanceTime(duration)
    fun reverseTime(duration: Duration) = baseClock.reverseTime(duration)
    fun setTime(instant: Instant) = baseClock.setTime(instant)

    override fun getMochaDay(): Long = calculateMochaEpochDay(now())

    override fun calculateMochaEpochDay(instant: Instant): Long {
        val tz = lock.withLock { currentTimeZone }
        val biologicalInstant = instant.minus(4.hours)
        return biologicalInstant.toLocalDateTime(tz).date.toEpochDays()
    }

    override fun getMillisAgo(duration: Duration): Long {
        val tz = lock.withLock { currentTimeZone }
        val targetMochaDay = getMochaDay() - duration.inWholeDays
        val targetDate = LocalDate.fromEpochDays(targetMochaDay.toInt())

        return targetDate.atTime(hour = 4, minute = 0)
            .toInstant(tz)
            .toEpochMilliseconds()
    }
}