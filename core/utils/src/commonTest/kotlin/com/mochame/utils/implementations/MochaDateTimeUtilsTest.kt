package com.mochame.utils.implementations

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.utils.di.UtilsModule
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private inline fun runEnv(crossinline block: DefaultMochaTimeUtils.(TestScope) -> Unit) =
    runUnitEnvironment<DefaultMochaTimeUtils>(
        koinSetup = { modules(UtilsModule::class) },
        block = block
    )

private val testZone = TimeZone.UTC
private val targetDate = LocalDate(2026, 6, 15)
private val expectedDayN = targetDate.toEpochDays()
private val expectedDayMinusOne = targetDate.toEpochDays() - 1L

class MochaDateTimeUtilsTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // TRANSITION BOUNDARIES
    // -----------------------------------------------------------

    @Test
    fun should_assignPreviousEpochDay_when_timeIsOneMillisecondBeforeCutoff() = runEnv {
        val instant = targetDate.atTime(3, 59, 59, 999_000_000).toInstant(testZone)

        val actualEpochDay = calculateMochaEpochDay(instant, testZone)

        assertEquals(
            expected = expectedDayMinusOne,
            actual = actualEpochDay,
            message = "03:59:59.999 must resolve to Day N - 1"
        )
    }

    @Test
    fun should_assignCurrentEpochDay_when_timeIsExactlyAtCutoff() = runEnv {
        val instant = targetDate.atTime(4, 0, 0, 0).toInstant(testZone)

        val actualEpochDay = calculateMochaEpochDay(instant, testZone)

        assertEquals(
            expected = expectedDayN,
            actual = actualEpochDay,
            message = "04:00:00.000 must resolve to Day N"
        )
    }

    @Test
    fun should_assignPreviousEpochDay_when_timeIsMidnight() = runEnv {
        val instant = targetDate.atTime(0, 0, 0, 0).toInstant(testZone)

        val actualEpochDay = calculateMochaEpochDay(instant, testZone)

        assertEquals(
            expected = expectedDayMinusOne,
            actual = actualEpochDay,
            message = "00:00:00.000 (Midnight) must resolve to Day N - 1"
        )
    }

    @Test
    fun should_assignPreviousEpochDay_when_telemetryLoggedAtTwoAm() = runEnv {
        val instant = targetDate.atTime(2, 0, 0, 0).toInstant(testZone)

        val actualEpochDay = calculateMochaEpochDay(instant, testZone)

        assertEquals(
            expected = expectedDayMinusOne,
            actual = actualEpochDay,
            message = "02:00:00.000 telemetry must belong to Day N - 1"
        )
    }

    // -----------------------------------------------------------
    // TIMEZONE HANDLING
    // -----------------------------------------------------------



}