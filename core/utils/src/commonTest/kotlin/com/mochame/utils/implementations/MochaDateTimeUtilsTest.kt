package com.mochame.utils.implementations

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.utils.di.UtilsModule
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private inline fun runEnv(crossinline block: DefaultMochaTimeUtils.(TestScope) -> Unit) =
    runUnitEnvironment<DefaultMochaTimeUtils>(
        koinSetup = { modules(UtilsModule::class) },
        block = block
    )

private val testZone = TimeZone.UTC
private val targetDate = LocalDate(2026, 6, 15)
private val expectedDayN = targetDate.toEpochDays()
private val expectedDayMinusOne = targetDate.toEpochDays() - 1L
private val nyZone = TimeZone.of("America/New_York")
private val londonZone = TimeZone.of("Europe/London")
private val epochZeroDate = LocalDate(1970, 1, 1) // Day 0L
private val epochMinusOneDate = LocalDate(1969, 12, 31) // Day -1L

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

    @Test
    fun should_guaranteeBijectiveSymmetry_acrossEntireBiologicalDay() = runEnv {
        val zone = TimeZone.of("Europe/London")
        val targetEpochDay = 20619L // 2026-06-15

        val targetLocalDate = LocalDate.fromEpochDays(targetEpochDay.toInt())
        val nextLocalDate = LocalDate.fromEpochDays((targetEpochDay + 1L).toInt())

        val mochaDayStartInstant = targetLocalDate.atTime(4, 0, 0, 0).toInstant(zone)
        val mochaDayEndInstant = nextLocalDate.atTime(3, 59, 59, 999_000_000).toInstant(zone)

        assertEquals(targetEpochDay, calculateMochaEpochDay(mochaDayStartInstant, zone))
        assertEquals(targetEpochDay, calculateMochaEpochDay(mochaDayEndInstant, zone))

        // Sweep
        var currentInstant = mochaDayStartInstant
        while (currentInstant <= mochaDayEndInstant) {
            val resolvedDay = calculateMochaEpochDay(currentInstant, zone)
            assertEquals(
                expected = targetEpochDay,
                actual = resolvedDay,
                message = "Instant $currentInstant must map to Epoch Day $targetEpochDay"
            )
            currentInstant += 1.hours
        }

        assertEquals(targetEpochDay + 1L, calculateMochaEpochDay(currentInstant, zone))
    }

    // -----------------------------------------------------------
    // TIMEZONE HANDLING
    // -----------------------------------------------------------

    @Test
    fun should_collapseIntoSameEpochDay_when_travelingEastAcrossDateLine() = runEnv {
        val tokyoZone = TimeZone.of("Asia/Tokyo")          // UTC+9
        val honoluluZone = TimeZone.of("Pacific/Honolulu") // UTC-10

        // User logs at 23:00 local in Tokyo before boarding flight
        val tokyoInstant = targetDate.atTime(23, 0, 0, 0).toInstant(tokyoZone)
        val tokyoEpochDay = calculateMochaEpochDay(tokyoInstant, tokyoZone)

        // User flies east across the International Date Line for 18 physical hours
        val honoluluInstant = tokyoInstant + 18.hours

        // User lands in Honolulu and logs at 22:00 local on that same calendar date
        val honoluluEpochDay = calculateMochaEpochDay(honoluluInstant, honoluluZone)

        assertEquals(
            expected = expectedDayN,
            actual = tokyoEpochDay,
            message = "Tokyo write at 23:00 must resolve to Day N"
        )
        assertEquals(
            expected = expectedDayN,
            actual = honoluluEpochDay,
            message = "Honolulu write at 22:00 (18 physical hours later) must resolve to Day N"
        )
        assertEquals(
            expected = tokyoEpochDay,
            actual = honoluluEpochDay,
            message = "Both writes must merge into the exact same DailyContext primary key"
        )
    }

    @Test
    fun should_skipCalendarDay_when_travelingWestAcrossDateLine() = runEnv {
        val honoluluZone = TimeZone.of("Pacific/Honolulu") // UTC-10
        val tokyoZone = TimeZone.of("Asia/Tokyo")          // UTC+9

        // User logs at 23:00 local in Honolulu on June 15
        val honoluluInstant = targetDate.atTime(23, 0, 0, 0).toInstant(honoluluZone)
        val honoluluEpochDay = calculateMochaEpochDay(honoluluInstant, honoluluZone)

        // 11 physical hours elapse during westward flight
        val tokyoInstant = honoluluInstant + 11.hours

        // User logs at 05:00 local in Tokyo on June 17
        val tokyoEpochDay = calculateMochaEpochDay(tokyoInstant, tokyoZone)

        assertEquals(
            expected = expectedDayN,
            actual = honoluluEpochDay,
            message = "Honolulu write must resolve to Day N (20619)"
        )
        assertEquals(
            expected = expectedDayN + 2L,
            actual = tokyoEpochDay,
            message = "Tokyo write must skip Day N+1 and resolve directly to Day N+2 (20621)"
        )
        assertEquals(
            expected = 2L,
            actual = tokyoEpochDay - honoluluEpochDay,
            message = "Westward cross-dateline transit creates a valid 1-day temporal gap"
        )
    }

    @Test
    fun should_respectCutoff_underExtremePositiveOffset_UTCPlus14() = runEnv {
        val zone = UtcOffset(hours = 14).asTimeZone()

        val beforeCutoff = targetDate.atTime(3, 59, 59, 999_000_000).toInstant(zone)
        val atCutoff = targetDate.atTime(4, 0, 0, 0).toInstant(zone)

        assertEquals(expectedDayMinusOne, calculateMochaEpochDay(beforeCutoff, zone))
        assertEquals(expectedDayN, calculateMochaEpochDay(atCutoff, zone))
    }

    @Test
    fun should_respectCutoff_underExtremeNegativeOffset_UTCMinus10() = runEnv {
        val zone = UtcOffset(hours = -10).asTimeZone()

        val beforeCutoff = targetDate.atTime(3, 59, 59, 999_000_000).toInstant(zone)
        val atCutoff = targetDate.atTime(4, 0, 0, 0).toInstant(zone)

        assertEquals(expectedDayMinusOne, calculateMochaEpochDay(beforeCutoff, zone))
        assertEquals(expectedDayN, calculateMochaEpochDay(atCutoff, zone))
    }

    @Test
    fun should_respectCutoff_underHalfHourOffset_India() = runEnv {
        val indiaZone = TimeZone.of("Asia/Kolkata") // UTC+05:30

        val beforeCutoff = targetDate.atTime(3, 59, 59, 999_000_000).toInstant(indiaZone)
        val atCutoff = targetDate.atTime(4, 0, 0, 0).toInstant(indiaZone)

        assertEquals(expectedDayMinusOne, calculateMochaEpochDay(beforeCutoff, indiaZone))
        assertEquals(expectedDayN, calculateMochaEpochDay(atCutoff, indiaZone))
    }

    @Test
    fun should_respectCutoff_underQuarterHourOffset_Kathmandu() = runEnv {
        val kathmanduZone = TimeZone.of("Asia/Kathmandu") // UTC+05:45

        val beforeCutoff = targetDate.atTime(3, 59, 59, 999_000_000).toInstant(kathmanduZone)
        val atCutoff = targetDate.atTime(4, 0, 0, 0).toInstant(kathmanduZone)

        assertEquals(expectedDayMinusOne, calculateMochaEpochDay(beforeCutoff, kathmanduZone))
        assertEquals(expectedDayN, calculateMochaEpochDay(atCutoff, kathmanduZone))
    }

    // -----------------------------------------------------------
    // DAYLIGHT SAVING
    // -----------------------------------------------------------

    @Test
    fun should_shiftCutoffToThreeAm_onNYFallBackMorning() = runEnv {
        val fallDate = LocalDate(2026, 11, 1)
        val expectedDayN = fallDate.toEpochDays()
        val expectedDayMinusOne = expectedDayN - 1L

        // 02:59:59.999 EST (1 ms before 4 physical hours have elapsed)
        val beforeFourPhysicalHours =
            LocalDateTime(2026, 11, 1, 2, 59, 59, 999_000_000).toInstant(nyZone)
        assertEquals(
            expected = expectedDayMinusOne,
            actual = calculateMochaEpochDay(beforeFourPhysicalHours, nyZone)
        )

        // 03:00:00.000 EST (Exact 4-physical-hour threshold after midnight -> Day N)
        val atFourPhysicalHours = LocalDateTime(2026, 11, 1, 3, 0, 0, 0).toInstant(nyZone)
        assertEquals(
            expected = expectedDayN,
            actual = calculateMochaEpochDay(atFourPhysicalHours, nyZone)
        )
    }

    @Test
    fun should_shiftCutoffToFiveAm_onUKSpringForwardMorning() = runEnv {
        val ukSpringDate = LocalDate(2026, 3, 29)
        val expectedDayN = ukSpringDate.toEpochDays()
        val expectedDayMinusOne = expectedDayN - 1L

        // 04:00:00 BST (03:00:00 GMT)
        val atFourAmBst = LocalDateTime(2026, 3, 29, 4, 0, 0, 0).toInstant(londonZone)
        assertEquals(
            expected = expectedDayMinusOne,
            actual = calculateMochaEpochDay(atFourAmBst, londonZone)
        )

        // 04:59:59.999 BST (1 millisecond before the effective 5:00 AM cutoff)
        val oneMsBeforeFiveAm =
            LocalDateTime(2026, 3, 29, 4, 59, 59, 999_000_000).toInstant(londonZone)
        assertEquals(
            expected = expectedDayMinusOne,
            actual = calculateMochaEpochDay(oneMsBeforeFiveAm, londonZone)
        )

        // 05:00:00.000 BST (Exact 4-physical-hour threshold)
        val atFiveAmBst = LocalDateTime(2026, 3, 29, 5, 0, 0, 0).toInstant(londonZone)
        assertEquals(
            expected = expectedDayN,
            actual = calculateMochaEpochDay(atFiveAmBst, londonZone)
        )
    }

    // -----------------------------------------------------------
    // EPOCH ZERO
    // -----------------------------------------------------------

    @Test
    fun should_resolveDayMinusOne_beforeCutoff_onEpochZero() = runEnv {
        val utcZone = TimeZone.UTC

        // 1970-01-01 00:00:00 UTC (The exact Unix epoch origin: minus 4h = 1969-12-31 20:00)
        val epochOrigin = Instant.fromEpochMilliseconds(0)
        assertEquals(-1L, calculateMochaEpochDay(epochOrigin, utcZone))

        // 1970-01-01 03:59:59.999 UTC (1 ms before cutoff)
        val beforeCutoff = LocalDateTime(1970, 1, 1, 3, 59, 59, 999_000_000).toInstant(utcZone)
        assertEquals(-1L, calculateMochaEpochDay(beforeCutoff, utcZone))
    }

    @Test
    fun should_respectLocalCutoff_atEpochZero_inNonUtcZones() = runEnv {
        val tokyoZone = TimeZone.of("Asia/Tokyo")       // UTC+9
        val nyZone = TimeZone.of("America/New_York")    // UTC-5 (EST)

        // Tokyo local: 1970-01-01 04:00:00 (1969-12-31 19:00:00 UTC) -> Day 0L
        val tokyoAtCutoff = LocalDateTime(1970, 1, 1, 4, 0, 0, 0).toInstant(tokyoZone)
        assertEquals(0L, calculateMochaEpochDay(tokyoAtCutoff, tokyoZone))

        // Tokyo local: 1970-01-01 03:59:59 (1969-12-31 18:59:59 UTC) -> Day -1L
        val tokyoBeforeCutoff =
            LocalDateTime(1970, 1, 1, 3, 59, 59, 999_000_000).toInstant(tokyoZone)
        assertEquals(-1L, calculateMochaEpochDay(tokyoBeforeCutoff, tokyoZone))

        // New York local: 1970-01-01 04:00:00 (1970-01-01 09:00:00 UTC) -> Day 0L
        val nyAtCutoff = LocalDateTime(1970, 1, 1, 4, 0, 0, 0).toInstant(nyZone)
        assertEquals(0L, calculateMochaEpochDay(nyAtCutoff, nyZone))

        // New York local: 1970-01-01 03:59:59 (1970-01-01 08:59:59 UTC) -> Day -1L
        val nyBeforeCutoff = LocalDateTime(1970, 1, 1, 3, 59, 59, 999_000_000).toInstant(nyZone)
        assertEquals(-1L, calculateMochaEpochDay(nyBeforeCutoff, nyZone))
    }

}