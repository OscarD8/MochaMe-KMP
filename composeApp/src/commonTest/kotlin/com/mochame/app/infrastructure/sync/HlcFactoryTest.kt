package com.mochame.app.infrastructure.sync

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.HLCTestEnvironment
import com.mochame.app.di.modules.AppModules
import com.mochame.app.domain.exceptions.MochaException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalKermitApi
class HlcFactoryTest : KoinTest {

    // --- TESTING COMPONENTS ---
    private val testModules = listOf(
        AppModules.hlcModule,
        CoreTestModules.hlcTestEnvironmentModule,
        CoreTestModules.testLoggingModule(minSeverity = Severity.Verbose)
    )

    private val APP_RELEASE_MS = 1740787200000L // March 2026
    private val ONE_DAY_MS = 86_400_000L
    private val MAX_COUNTER = 65535             // 16-bit limit

    // --- TESTING SETUP/TEARDOWN ---
    @BeforeTest
    fun setup() {
        startKoin {
            modules(testModules)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun runTestWrapper(block: suspend HLCTestEnvironment.(TestScope) -> Unit) = runTest {
        val env: HLCTestEnvironment by inject()

        try {
            env.block(this)
        } finally {
            env.writer.reset()
        }
    }

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun should_initialize_valid_hlc_when_new_install() = runTestWrapper {
        // Given: wallClock is March 2026
        fakeClock.setTime(APP_RELEASE_MS)

        // When: First hydration with no history
        val result = factory.hydrate(null, "node-1")

        // Then: TS is exactly wallClock, count is 0
        assertEquals(APP_RELEASE_MS, result.ts)
        assertEquals(0, result.count)
    }

    @Test
    fun should_return_success_with_restamped_node_id_when_valid_history_provided() =
        runTestWrapper {
            // Given
            val history = "1740787200000:5:device-a"
            val newNodeId = "device-b"

            // When
            val result = factory.hydrate(history, newNodeId)

            // Then
            assertNotNull(result)
            assertEquals("1740787200000:5:device-b", result.toString())
        }

    @Test
    fun should_log_warning_but_succeed_when_future_jump_detected() = runTestWrapper {
        // Given: Wall clock is 2 years ahead of history
        val historyTs = APP_RELEASE_MS
        val wallClock = historyTs + (ONE_DAY_MS * 730) // 2 years
        fakeClock.setTime(wallClock)

        // When
        factory.hydrate("$historyTs:0:node-old", "node-new")

        // Then
        assertTrue(writer.logs.any { it.message.contains("Future Jump") })
        assertTrue(writer.logs.any { it.message.contains("Successfully reconciled") })
    }

    @Test
    fun should_persist_new_node_id_for_subsequent_hlcs_after_migration() = runTestWrapper {
        // Given: History from "node-old"
        val history = "${APP_RELEASE_MS}:10:node-old"
        factory.hydrate(history, "node-new")

        // When: Generating the next HLC
        val next = factory.getNextHlc()

        // Then: It must use "node-new"
        assertEquals("node-new", next.nodeId, "Factory failed to adopt the new NodeID after hydration.")
        assertEquals(11, next.count)
    }

    // -----------------------------------------------------------
    // COUNTER
    // -----------------------------------------------------------
    @Test
    fun should_increment_logical_counter_when_wall_clock_has_not_advanced() = runTestWrapper {
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
    fun should_reset_counter_to_zero_when_wall_clock_moves_forward() = runTestWrapper {
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

    @Test
    fun should_reset_counter_to_zero_during_migration_if_wall_clock_is_ahead() = runTestWrapper {
        // Given: History is older than current wall clock
        val olderHistory = "${APP_RELEASE_MS - 1000}:50:node-old"
        val wallClock = APP_RELEASE_MS + 1000
        fakeClock.setTime(wallClock)

        // When
        val result = factory.hydrate(olderHistory, "node-new")

        // Then: TS pins to wall clock, counter resets
        assertEquals(wallClock, result.ts)
        assertEquals(0, result.count)
        assertEquals("node-new", result.nodeId)
    }

    @Test
    fun should_preserve_history_counter_during_migration_if_wall_clock_is_behind() =
        runTestWrapper {
            // Given: History is newer than current wall clock (but within 24h)
            val newerHistoryTs = APP_RELEASE_MS + 5000
            val newerHistory = "$newerHistoryTs:99:node-old"
            fakeClock.setTime(APP_RELEASE_MS) // Wall clock is behind

            // When
            val result = factory.hydrate(newerHistory, "node-new")

            // Then: TS pins to history, counter is preserved
            assertEquals(newerHistoryTs, result.ts)
            assertEquals(99, result.count)
            assertEquals("node-new", result.nodeId)
        }

    @Test
    fun should_stall_immediately_after_migration_if_history_counter_is_exhausted() = runTestWrapper { scope ->
        // Given: History is at the limit, wall clock is behind
        val futureTs = APP_RELEASE_MS + 5000
        val maxHistory = "$futureTs:$MAX_COUNTER:node-old"
        fakeClock.setTime(APP_RELEASE_MS) // Device is behind history

        factory.hydrate(maxHistory, "node-new")

        // When: First call to getNextHlc
        var capturedHlc: HLC? = null
        val job = scope.launch {
            capturedHlc = factory.getNextHlc()
        }
        yield()

        // Then: It must stall because it can't increment 65535 and time hasn't caught up
        assertNull(capturedHlc, "Factory should have stalled; counter was already at MAX from history.")

        fakeClock.advanceTime(6000) // Move past history TS
        job.join()

        assertNotNull(capturedHlc)
        assertEquals(0, capturedHlc.count)
        assertEquals(APP_RELEASE_MS + 6000, capturedHlc.ts)
        job.cancel()
    }

    // -----------------------------------------------------------
    // MONOTONICITY AND CONCURRENCY
    // -----------------------------------------------------------
    @Test
    fun should_ignore_second_hydration_and_log_warning() = runTestWrapper {
        // Given
        factory.hydrate(null, "node-1")

        // When
        val secondResult = factory.hydrate("12345:0:node-2", "node-2")

        // Then: Returns the first hydration result
        assertTrue(writer.logs.any { it.message.contains("rehydrate") })
        assertEquals("node-1", secondResult.nodeId)
    }

    @Test
    fun should_yield_and_wait_for_next_tick_when_counter_overflows() = runTestWrapper {
        // Given: Hydrate at the 16-bit limit (65535)
        val maxCounterHlc = "${fakeClock.now().toEpochMilliseconds()}:65535:node-1"
        factory.hydrate(maxCounterHlc, "node-1")

        // When: We try to get the next HLC in a separate coroutine
        var resultHlc: HLC? = null
        val job = it.launch {
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

    @Test
    fun should_maintain_monotonicity_when_multi_threaded_push_for_nextHlc() =
        runTestWrapper { scope ->
            // Arrange: Multi-threaded simulation suspended with CompletableDeferred
            val threadCount = 30
            val iterations = 50
            val gate = CompletableDeferred<Unit>()
            fakeClock.setTime(APP_RELEASE_MS + 1000L)
            factory.hydrate(null, "node")

            val workerDeferreds = List(threadCount) { workerId ->
                scope.async(Dispatchers.Default) {
                    gate.await()
                    val localResults = mutableListOf<HLC>()
                    repeat(iterations) {
                        val current = factory.getNextHlc()
                        if (localResults.isNotEmpty()) {
                            assertTrue(
                                current > localResults.last(),
                                "Local Monotonicity Failure: $current <= ${localResults.last()}"
                            )
                        }
                        localResults.add(current)
                    }
                    localResults
                }
            }

            // Act I: FIRE and await all promised individual thread results
            gate.complete(Unit)

            val allResults = workerDeferreds.awaitAll().flatten()

            // Assert: Distinct results across all threads matching expected total
            val totalExpected = threadCount * iterations
            assertEquals(
                totalExpected, allResults.distinct().size,
                "Causality Violation: Duplicate HLCs detected across threads!"
            )

            // Act II: introduce clock skew and fetch a new HLC
            fakeClock.reverseTime(500L)
            val skewedSentinel = factory.getNextHlc()

            // Assert: the skewed sentinel is still the greatest value
            val maxProduced = allResults.maxOrNull()!!
            assertTrue(
                skewedSentinel > maxProduced,
                "Causality Broken possibly by Clock Skew! Sentinel $skewedSentinel is not > $maxProduced"
            )
            assertEquals(
                maxProduced.ts,
                skewedSentinel.ts,
                "HLC should have pinned to the max known physical time during skew."
            )
            assertTrue(
                skewedSentinel.count > maxProduced.count,
                "Logical counter failed to increment during physical clock regression."
            )
        }

    // Demo test purely to better understand job hierarchies and yield()
    @Test
    fun testSingleStep() = runTest {
        var count = 0
        val job = launch {
            while (isActive) {
                count++
                yield() // The "infinite" pinger
            }
        }

        // Instead of runCurrent(), which seems to reschedule immediately on the schedular
        yield()
        // Now count is 1. The test yielded, the loop ran once, then it yielded back to the test.
        assertEquals(1, count)

        yield()
        // Now count is 2.
        assertEquals(2, count)

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun should_yield_and_retry_when_counter_is_exhausted() = runTestWrapper { scope ->
        // Arrange: Hit the counter limit at a certain time
        fakeClock.setTime(APP_RELEASE_MS)
        val initialHlc = "$APP_RELEASE_MS:$MAX_COUNTER:node-1"
        factory.hydrate(initialHlc, "node-1")

        // Act: Launch a coroutine to make the 65,536th call
        var capturedHlc: HLC? = null
        val stallingJob = scope.launch {
            capturedHlc = factory.getNextHlc()
        }
        // -- Yield the test coroutine, so the child job runs and hits its own yield
        yield()

        // Assert: The job should be suspended (Case C)
        assertNull(capturedHlc, "Factory should have yielded, but it returned an HLC!")
        assertFalse(stallingJob.isCompleted, "Job should be stuck in the yield loop")

        // Act: Advance the fake clock by 1ms
        fakeClock.advanceTime(1)
        // -- Wait for the child job to see the new time and hit Case A
        stallingJob.join()

        // Assert:
        val exhaustionLog = writer.logs.any { it.message.contains("Counter Exhausted") }
        val logCount = writer.logs.count { it.message.contains("Counter Exhausted") }
        assertTrue(exhaustionLog, "Should have logged a warning about exhaustion")
        assertEquals(1, logCount, "Concurrency problem - only one yield should have occurred.")

        assertNotNull(capturedHlc)
        assertEquals(APP_RELEASE_MS + 1, capturedHlc.ts, "New HLC should use the new millisecond")
        assertEquals(0, capturedHlc.count, "Counter should have reset to 0")
    }

    @Test
    fun should_only_initialize_one_new_install_when_init_under_contention() =
        runTestWrapper { scope ->
            val threadCount = 30
            val gate = CompletableDeferred<Unit>()
            val nodeId = "node-1"

            val jobs = List(threadCount) {
                scope.launch(Dispatchers.IO) {
                    gate.await() // THE STARTING GUN (gemini)
                    factory.hydrate(null, nodeId)
                }
            }
            gate.complete(Unit)
            jobs.joinAll()

            // Verification
            val initLogs = writer.logs.filter { it.message.contains("New Install") }
            val rehydrateLogs = writer.logs.filter { it.message.contains("rehydrate") }
            assertEquals(
                1,
                initLogs.size,
                "Race Condition! Multiple threads triggered 'New Install' logic."
            )
            assertEquals(
                threadCount - 1,
                rehydrateLogs.size,
                "Race Condition! Multiple threads triggered 'New Install' logic."
            )

        }

    // -----------------------------------------------------------
    // HYDRATION PARSING EXCEPTIONS
    // -----------------------------------------------------------
    @Test
    fun should_throw_HlcParseException_when_string_has_incorrect_number_of_parts() =
        runTestWrapper {
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
    fun should_throw_HlcParseException_when_counter_is_not_a_valid_int() = runTestWrapper {
        // Given
        val corruptInput = "1740787200000:abc:device-a"

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    @Test
    fun should_throw_parse_exception_when_node_id_is_blank() = runTestWrapper {
        // Given
        val corruptInput = "1740787200000:0: " // Blank NodeID

        // When / Then
        assertFailsWith<MochaException.Persistent.HlcParseException> {
            HLC.parse(corruptInput)
        }
    }

    // -----------------------------------------------------------
    // HYDRATION FAILURE
    // -----------------------------------------------------------
    @Test
    fun should_throw_ClockSkew_when_history_is_poisoned_with_future_date() = runTestWrapper {
        // Given: System clock is March 2026, but history is Jan 2040
        val futureTs = 2209032000000L
        val poisonedHlc = "$futureTs:0:node-old"

        // When / Then
        assertFailsWith<MochaException.Persistent.ClockSkew> {
            factory.hydrate(poisonedHlc, "node-new")
        }

        assertTrue(writer.logs.any { it.message.contains("future drift") })
    }

    @Test
    fun should_report_clock_skew_when_system_time_is_before_2026_floor() = runTestWrapper {
        // Given
        fakeClock.setTime(1000L) // Set back to Jan 1st, 1970

        // When & Then
        val exception = assertFailsWith<MochaException.Persistent.ClockSkew> {
            factory.hydrate(null, "node-1")
        }

        assertTrue(exception.driftDisplay > 0, "Drift should be a positive value")
    }

    @Test
    fun should_log_warning_when_counter_exhaustion_triggers_yield() = runTestWrapper { scope ->
        // When: The factory at the 16-bit limit
        val maxCounterHlc = "${fakeClock.now().toEpochMilliseconds()}:65535:node-1"
        factory.hydrate(maxCounterHlc, "node-1")

        // Then: This will trigger the 'else' block (Case C)
        scope.launch { factory.getNextHlc() }
        yield()
        fakeClock.advanceTime(1)

        // Verify the log recorded the "Stalling" event
        val logs = writer.logs
        val counterWarning = logs.find { it.message.contains("Counter Exhausted") }

        assertNotNull(counterWarning, "Missing visibility into counter exhaustion!")
        assertEquals(Severity.Warn, counterWarning.severity)
    }

    // -----------------------------------------------------------
    // LOGGING TEST
    // -----------------------------------------------------------
    @Test
    fun should_log_error_when_clock_skew_detected() = runTestWrapper { scope ->
        fakeClock.setTime(1000L) // 1970

        assertFailsWith<MochaException.Persistent.ClockSkew> {
            factory.hydrate(null, "node-1")
        }

        val skewError = writer.logs.find { it.severity == Severity.Error }
        assertEquals(true, skewError?.message?.contains("Clock Skew"))
    }
}