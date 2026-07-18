@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.domain

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.TestHlcFactory
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.di.domain.PruneEntriesTestEnv
import com.mochame.sync.di.domain.PruneEntriesUseCaseTestApp
import com.mochame.sync.fakes.createTestSyncIntent
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.yield
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend PruneEntriesTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment(
        koinSetup = { includes(koinConfiguration<PruneEntriesUseCaseTestApp>()) },
        block = block
    )

internal const val TEST_PRUNE_DAYS = 30

class PruneEntriesUseCaseTest : MochaPlatformTest() {

    @Test
    fun should_returnZeroAndLeaveStoreEmpty_when_noEligibleEntriesExist() = runEnv {
        fakeStore.reset()

        val totalDeleted = useCase()

        assertEquals(0, totalDeleted, "The use case must return exactly 0 deletions.")
        assertTrue(
            fakeStore.intents.isEmpty(),
            "The intent store must remain completely empty."
        )

        val containsCompletionLog =
            logWriter.logs.any { it.message.contains("Prune Complete") }
        assertFalse(
            containsCompletionLog,
            "Completion summaries must not be logged when zero rows are removed."
        )
    }

    @Test
    fun should_executeExactlyTwoLoopIterationsAndYieldOnce_when_entriesAreBelowLimit() =
        runEnv {
            // Arrange
            fakeStore.reset()
            val targetCount = 45
            val chronologicalHlcs = TestHlcFactory.chronologicalSequence(targetCount)

            val eligibleIntents = chronologicalHlcs.map { hlc ->
                createTestSyncIntent(hlc = hlc, status = SyncStatus.SUCCESS)
            }

            fakeStore.seedIntents(eligibleIntents)

            // Act
            val totalDeleted = useCase()

            // Assert
            assertEquals(
                targetCount,
                totalDeleted,
                "The use case must return exactly 45 deletions."
            )
            assertTrue(
                fakeStore.intents.isEmpty(),
                "The intent store must be left empty after pruning completes."
            )

            val completionLog =
                logWriter.logs.find { it.message.contains("Prune Complete") }

            assertTrue(
                completionLog != null,
                "The final execution performance metric log must be present."
            )
            assertTrue(
                completionLog.message.contains("Total: 45"),
                "Log metrics must accurately capture the 45 deleted entries."
            )
            assertTrue(
                completionLog.message.contains("Chunks: 1"),
                "The log must confirm the loop ran exactly twice (Chunks: 1), verifying a single yield execution path."
            )
        }

    @Test
    fun should_executeExactlyFourLoopIterationsAndYieldThreeTimes_when_entriesExceedLimit() =
        runEnv {
            // Arrange
            fakeStore.reset()

            val targetCount = 250
            val chronologicalHlcs = TestHlcFactory.chronologicalSequence(targetCount)

            val eligibleIntents = chronologicalHlcs.map { hlc ->
                createTestSyncIntent(hlc = hlc, status = SyncStatus.SUCCESS)
            }

            fakeStore.seedIntents(eligibleIntents)

            // Act
            val totalDeleted = useCase()

            // Assert
            assertEquals(
                targetCount,
                totalDeleted,
                "The use case must return exactly 250 deletions."
            )
            assertTrue(
                fakeStore.intents.isEmpty(),
                "The intent store must be completely cleared after all chunks prune."
            )

            val completionLog =
                logWriter.logs.find { it.message.contains("Prune Complete") }

            assertTrue(
                completionLog != null,
                "The final execution metrics log must be generated."
            )
            assertTrue(
                completionLog.message.contains("Total: 250"),
                "Log metrics must report a total of 250 entries removed."
            )

            // Verifying Chunks: 4 proves that the loop chunked the executions into pages of:
            // Pass 1: 100 deleted (yields) -> Pass 2: 100 deleted (yields) -> Pass 3: 50 deleted (yields) -> Pass 4: 0 deleted (breaks)
            assertTrue(
                completionLog.message.contains("Chunks: 3"),
                "The log telemetry must confirm the loop executed exactly 3 times (Chunks: 3), validating 3 active cooperative yields."
            )
        }

    @Test
    fun should_pruneOnlyEntriesOlderThanCutoff_when_evaluatingTemporalEdgeBoundaries() =
        runEnv {
            // Arrange
            fakeStore.reset()

            val baseTestingTime = 1740787200000L // Thursday, Feb 27, 2025
            fakeClock.setTime(baseTestingTime)

            // Calculate the exact millisecond cutoff value
            val cutoffThresholdMs = fakeClock.getMillisForDaysAgo(TEST_PRUNE_DAYS)

            // Distinct HLC identifiers
            val hlcs = TestHlcFactory.chronologicalSequence(2)
            val olderHlc = hlcs[0]
            val youngerHlc = hlcs[1]

            // Entry positioned exactly 1 millisecond before the boundary line (eligible)
            val olderIntent = createTestSyncIntent(
                hlc = olderHlc,
                createdAt = cutoffThresholdMs - 1L,
                status = SyncStatus.SUCCESS
            )

            // Entry positioned exactly 1 millisecond after the boundary line (ineligible)
            val youngerIntent = createTestSyncIntent(
                hlc = youngerHlc,
                status = SyncStatus.SUCCESS,
                createdAt = cutoffThresholdMs + 1L
            )

            fakeStore.seedIntents(olderIntent, youngerIntent)

            // Act
            val totalDeleted = useCase()

            // Assert
            assertEquals(
                1,
                totalDeleted,
                "The use case must return exactly 1 deletion for the expired entry."
            )

            val remainingIntents = fakeStore.intents
            assertEquals(
                1,
                remainingIntents.size,
                "The store must retain exactly one intent."
            )
            assertEquals(
                youngerHlc,
                remainingIntents.first().hlc,
                "The younger intent (cutoff + 1ms) must be strictly preserved to confirm no off-by-one errors."
            )
        }

    @Test
    fun should_abortExecutionAndThrowCancellationException_when_coroutineContextIsCancelledMidProcess() = runEnv { scope ->
        // Arrange: seed entries exceeding the 100-limit chunk size
        fakeStore.reset()

        val targetCount = 250
        val chronologicalHlcs = TestHlcFactory.chronologicalSequence(targetCount)

        val eligibleIntents = chronologicalHlcs.map { hlc ->
            createTestSyncIntent(
                hlc = hlc,
                status = SyncStatus.SUCCESS,
                createdAt = 0L
            )
        }

        fakeStore.seedIntents(eligibleIntents)

        // Act: Launch inside an async deferred block to capture its cancellation lifecycle
        val useCaseDeferred = scope.async {
            useCase()
        }

        // Launch a concurrent monitoring coroutine on the same virtual test scheduler.
        // This monitor waits for the first chunk (100 items) to be wiped out, then cancels the parent job.
        scope.launch {
            while (fakeStore.intents.size == 250) {
                yield() // Cooperatively hand execution back to the use case until the first pass completes
            }
            // The first chunk has processed (size dropped to 150). Trigger mid-execution cancellation.
            useCaseDeferred.cancel()
        }

        assertFailsWith<CancellationException> {
            useCaseDeferred.await()
        }

        assertEquals(
            150,
            fakeStore.intents.size,
            "The orchestrator must immediately stop processing further chunks, leaving exactly 150 items remaining."
        )
    }
}
