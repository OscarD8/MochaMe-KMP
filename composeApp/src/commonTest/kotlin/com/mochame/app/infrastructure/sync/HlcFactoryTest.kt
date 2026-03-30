package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.di.modules.AppModules
import com.mochame.app.domain.system.exceptions.MochaException
import com.mochame.app.infrastructure.fakeutils.FakeDateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalKermitApi
class HlcFactoryTest : KoinTest {

    // --- TESTING COMPONENTS ---
    private val testModules = listOf(
        AppModules.hlcModule,
        CoreTestModules.fakeDateTimeUtilsModule,
        CoreTestModules.testLoggingModule(TestTag.CORE)
    )
    private val fakeClock: FakeDateTimeUtils by inject()
    private val factory: HlcFactory by inject()
    private val testLogWriter: TestLogWriter by inject()

    // --- TESTING SETUP/TEARDOWN ---
    @BeforeTest
    fun setup() {
        startKoin {
            modules(testModules)
        }
    }

    @AfterTest
    fun tearDown() {
        testLogWriter.reset()
        stopKoin()
    }

    // --- HYDRATION TESTS ---
    @Test
    fun should_return_success_with_restamped_node_id_when_valid_history_provided() = runTest {
        // Given
        val history = "1740787200000:5:device-a"
        val newNodeId = "device-b"

        // When
        val result = factory.hydrate(history, newNodeId)

        // Then
        assertNotNull(result)
        assertEquals("1740787200000:5:device-b", result.toString())
    }

    // --- MONOTONICITY & COUNTER TESTS ---
    @Test
    fun should_increment_logical_counter_when_wall_clock_has_not_advanced() = runTest {
        // Given
        factory.hydrate(null, "node-1")

        // When
        val first = factory.getNextHlc()
        val second = factory.getNextHlc()

        // Then
        assertEquals(1, first.count)
        assertEquals(2, second.count)
        assertEquals(first.ts, second.ts)
    }

    @Test
    fun should_reset_counter_to_zero_when_wall_clock_moves_forward() = runTest {
        // Given
        factory.hydrate(null, "node-1")
        val first = factory.getNextHlc()

        // When
        fakeClock.advanceTime(1)
        val second = factory.getNextHlc()

        // Then
        assertEquals(0, second.count)
        assertTrue(second.ts > first.ts)
    }

    // --- EDGE CASE: COUNTER OVERFLOW ---
    @Test
    fun should_yield_and_wait_for_next_tick_when_counter_overflows() = runTest {
        // Given: Hydrate at the 16-bit limit (65535)
        val maxCounterHlc = "${fakeClock.now().toEpochMilliseconds()}:65535:node-1"
        factory.hydrate(maxCounterHlc, "node-1")

        // When: We try to get the next HLC in a separate coroutine
        var resultHlc: HLC? = null
        val job = launch {
            resultHlc = factory.getNextHlc()
        }

        // Then: It should suspend/yield because the counter is exhausted
        yield()
        assertNull(resultHlc, "Factory should be suspended in busy-wait yield")

        // When: Time finally ticks forward
        fakeClock.advanceTime(1)
        yield()

        // Then: The suspended call completes with a reset counter
        assertNotNull(resultHlc)
        assertEquals(0, resultHlc.count)
        job.cancel()
    }

    // --- CONCURRENCY TESTS ---
    @Test
    fun should_maintain_unique_monotonic_sequence_under_high_concurrency() = runTest {
        // Given
        factory.hydrate(null, "node-1")
        val results = mutableSetOf<String>()
        val count = 1000

        // When: Blasting the factory with 1000 simultaneous requests
        List(count) {
            launch {
                val hlc = factory.getNextHlc()
                results.add(hlc.toString())
            }
        }.joinAll()

        // Then: Every single HLC must be unique
        assertEquals(count, results.size, "Found duplicate HLCs during high contention")
    }

    @Test
    fun should_generate_strictly_monotonic_sequence_under_parallel_blast() = runTest {
        // GIVEN: A hydrated factory and a high-concurrency parallel environment
        factory.hydrate(null, "node-1")
        val totalRequests = 1000
        val mutex = Mutex()
        val results = mutableListOf<String>()

        // WHEN: A background virtual clock advances time while parallel jobs blast the factory
        val virtualClock = launch {
            while (isActive) {
                fakeClock.advanceTime(1)
                yield() // Allow blast jobs to catch the new millisecond
            }
        }

        List(totalRequests) {
            launch(Dispatchers.Default) {
                val hlc = factory.getNextHlc()
                mutex.withLock {
                    results.add(hlc.toString())
                }
            }
        }.joinAll()

        virtualClock.cancel()

        // THEN: The sequence must be unique and strictly monotonic
        assertEquals(totalRequests, results.size, "Total HLC count mismatch")

        // Sort
        val sortedResults = results.sorted()

        // Verify uniqueness (no two HLCs are the same)
        assertEquals(totalRequests, results.toSet().size, "Duplicate HLCs detected")

        // Verify strict monotonicity: Every HLC must be greater than the one before it
        for (i in 1 until sortedResults.size) {
            val prev = sortedResults[i - 1]
            val current = sortedResults[i]

            assertTrue(
                actual = current > prev,
                message = "Causality Violation at index $i: $current is not greater than $prev"
            )
        }
    }

    // --- HLC PARSING EXCEPTIONS ---
    @Test
    fun should_throw_HlcParseException_when_string_has_incorrect_number_of_parts() = runTest {
        // Given
        val corruptInput = "1740787200000:0" // Missing NodeID

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    @Test
    fun should_throw_HlcParseException_when_timestamp_is_not_a_valid_long() {
        // Given
        val corruptInput = "not_a_long:0:device-a"

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    @Test
    fun should_throw_HlcParseException_when_counter_is_not_a_valid_int() = runTest {
        // Given
        val corruptInput = "1740787200000:abc:device-a"

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    @Test
    fun should_throw_parse_exception_when_node_id_is_blank() = runTest {
        // Given
        val corruptInput = "1740787200000:0: " // Blank NodeID

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    // --- FACTORY HYDRATION FAILURE RESULTS ---
    @Test
    fun should_return_invalid_data_when_hlc_string_is_unparseable() = runTest {
        // Given
        val corruptHlc = "not-an-hlc"

        // When & Then
        val exception = assertFailsWith<MochaException.Persistent.HlcParseException> {
            factory.hydrate(corruptHlc, "node-1")
        }
    }

    @Test
    fun should_report_clock_skew_when_system_time_is_before_2026_floor() = runTest {
        // Given
        fakeClock.setTime(1000L) // Set back to Jan 1st, 1970

        // When & Then
        val exception = assertFailsWith<MochaException.Persistent.ClockSkew> {
            factory.hydrate(null, "node-1")
        }

        assertTrue(exception.driftMs > 0, "Drift should be a positive value")
    }

    // --- FACTORY HYDRATION FAILURE RESULTS ---
    @Test
    fun should_log_warning_when_counter_exhaustion_triggers_yield() = runTest {
        // When: The factory at the 16-bit limit
        val maxCounterHlc = "${fakeClock.now().toEpochMilliseconds()}:65535:node-1"
        factory.hydrate(maxCounterHlc, "node-1")

        // Then: This will trigger the 'else' block (Case C)
        launch { factory.getNextHlc() }
        yield()
        fakeClock.advanceTime(1)

        // Verify the log recorded the "Stalling" event
        val logs = testLogWriter.logs
        val counterWarning = logs.find { it.message.contains("Counter Exhausted") }

        assertNotNull(counterWarning, "Missing visibility into counter exhaustion!")
        assertEquals(Severity.Warn, counterWarning.severity)
    }

    @Test
    fun should_log_error_when_clock_skew_detected() = runTest {
        fakeClock.setTime(1000L) // 1970

        assertFailsWith<MochaException.Persistent.ClockSkew> {
            factory.hydrate(null, "node-1")
        }

        val skewError = testLogWriter.logs.find { it.severity == Severity.Error }
        assertEquals(true, skewError?.message?.contains("Clock Skew"))
    }
}