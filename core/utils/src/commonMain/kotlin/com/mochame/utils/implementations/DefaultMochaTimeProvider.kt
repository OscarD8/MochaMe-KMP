package com.mochame.utils.implementations

import com.mochame.utils.interfaces.MochaTimeProvider
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Single(binds = [MochaTimeProvider::class])
open class DefaultMochaTimeProvider : MochaTimeProvider {

    override fun now(): Instant = Clock.System.now()

    override fun getMochaDay(): Long {
        return calculateMochaEpochDay(now())
    }

    override fun calculateMochaEpochDay(instant: Instant): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val biologicalInstant = instant.minus(4.hours)
        return biologicalInstant.toLocalDateTime(timeZone).date.toEpochDays()
    }

    override fun getMillisAgo(duration: Duration): Long {
        val timeZone = TimeZone.currentSystemDefault()
        val targetMochaDay = getMochaDay() - duration.inWholeDays
        val targetDate = LocalDate.fromEpochDays(targetMochaDay.toInt())

        return targetDate.atTime(hour = 4, minute = 0)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

}

