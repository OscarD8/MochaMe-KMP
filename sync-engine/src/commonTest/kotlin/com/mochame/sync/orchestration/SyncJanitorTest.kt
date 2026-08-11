@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.orchestration

import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.support.MochaPlatformTest
import com.mochame.utils.fixtures.HlcTestFactory
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.di.janitor.JanitorTestApp
import com.mochame.sync.di.janitor.JanitorTestEnv
import com.mochame.sync.fixtures.createTestSyncIntent
import com.mochame.sync.spi.node.NodeContext
import com.mochame.utils.fixtures.TestPayloads
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.io.Buffer
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend JanitorTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<JanitorTestEnv>(
        koinSetup = { includes(koinConfiguration<JanitorTestApp>()) },
        block = block
    )


@ExperimentalCoroutinesApi
class SyncJanitorTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // BOOT LIFECYCLE & EXCEPTIONS / STATE GUARDS (HLC/BOOT)
    // -----------------------------------------------------------

    @Test
    fun yay_or_nay_onNodeEstablishmentCall() = runEnv { scope ->
        janitor.startupChecks()

        scope.advanceUntilIdle()

        assertNotNull(nodeManager.getOrEstablishContext())
    }

    @Test
    fun should_transitionBootStateAndHydrateHlcFactory_when_executingAgainstValidStartupState() =
        runEnv { scope ->
            // Given
            assertEquals(BootState.Idle, bootUpdater.bootState.value)
            nodeManager.forcedNextNodeId = "node-alpha"

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertEquals(
                listOf(BootState.Idle, BootState.Initializing, BootState.Ready),
                bootUpdater.history
            )
            assertEquals(
                1,
                hlcFactory.hydrateCallCount,
                "HLC factory must be hydrated exactly once during startup."
            )
            assertEquals(
                "node-alpha",
                hlcFactory.lastHydratedNodeId,
                "HLC factory must be hydrated with the current node ID."
            )
        }

    @Test
    fun should_abortStartupChecksAndSkipHydration_when_bootStateIsInitializing() =
        runEnv { scope ->
            // Given
            bootUpdater.updateBootState(BootState.Initializing)

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertEquals(
                0,
                hlcFactory.hydrateCallCount,
                "HLC Factory must not be hydrated if boot checks short-circuit."
            )

            val skipLog = writer.logs.find { it.message.contains("Skipping startup") }
            assertNotNull(
                skipLog,
                "Janitor must log skipping startup when in an invalid boot state."
            )
        }

    @Test
    fun should_abortStartupChecks_when_bootStatIsInCriticalFailure() =
        runEnv { scope ->
            val failure = MochaException.Persistent.ClockSkew(5.seconds)
            bootUpdater.updateBootState(BootState.CriticalFailure("Failed", failure))

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertEquals(0, hlcFactory.hydrateCallCount)
        }

    @Test
    fun should_enterCriticalBootFailure_when_lastHlcIsFromTheFuture() =
        runEnv {
            // Arrange
            // Seed a Future HLC (2040-01-01...)
            val futureHlc = HLC.parse("2209032000000:0000:node-1")

            nodeManager.updateHlcFloor(futureHlc)

            // Act
            janitor.startupChecks()

            // Assert
            bootUpdater.bootState.test {
                // Skip Idle
                assertEquals(BootState.Idle, awaitItem())

                // Skip Initializing
                assertTrue(awaitItem() is BootState.Initializing)

                // Capture the Critical Failure
                val finalState = awaitItem()
                assertTrue(finalState is BootState.CriticalFailure)

                assertTrue(finalState.exception is MochaException.Persistent.ClockSkew)

                // Verify the logs
                val log = writer.logs.find { it.message.contains("Clock Skew") }
                assertNotNull(log, "The clock skew log should have been recorded!")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun should_reportTransientFailure_when_bootHydrationTimesOut() =
        runEnv { scope ->
            // Arrange - simulate the manager being locked
            nodeManager.simulatedDelay = 6.seconds

            // Act
            janitor.startupChecks()
            // janitor stalls on hydration, locked from fetching device context
            scope.runCurrent()
            assertEquals(BootState.Initializing, bootUpdater.bootState.value)
            scope.advanceTimeBy(5001.milliseconds)

            // Assert
            val finalState = bootUpdater.bootState.value
            assertTrue(
                finalState is BootState.TransientFailure,
                "Janitor should have failed on timeout. Got $finalState.."
            )
            assertTrue(finalState.exception is MochaException.Transient.BootTimeout)
        }

    @Test
    fun should_setCriticalBootFailure_when_janitorsOwnLockIsBusy() =
        runEnv { scope ->
            // Given
            janitorMutex.lock()

            // When
            janitor.startupChecks()

            // Then
            bootUpdater.bootState.test {
                assertEquals(BootState.Idle, awaitItem())
                expectNoEvents()

                scope.advanceTimeBy(5001L.milliseconds)
                expectNoEvents() // -- should not have hit internal timeout

                scope.advanceTimeBy(15_001L.milliseconds)
                val failureState = awaitItem()

                assertTrue(failureState is BootState.TransientFailure)
                assertTrue(failureState.exception is MochaException.Transient.BootTimeout)
            }

            janitorMutex.unlock()
        }

    @Test
    fun should_catchAndTransitionToCriticalFailure_when_executionPolicyThrowsDatabaseErrorWithinBootTimeAllocation() =
        runEnv { scope ->
            // Given
            val dbLockException = IllegalStateException("Database locked / busy")
            executor.failConsecutively(count = 1, exception = dbLockException)

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertEquals(1, executor.executionCount)
            assertTrue(executor.executionHistory.contains("[Startup Checks]"))

            val history = bootUpdater.history
            assertEquals(
                2,
                history.size,
                "Boot history should only record Idle and CriticalFailure."
            )
            assertEquals(BootState.Idle, history[0])
            assertTrue(
                history[1] is BootState.CriticalFailure,
                "Final boot state must be CriticalFailure. Got: ${history[1]}"
            )

            val errorLog =
                writer.logs.find { it.message.contains("Critical boot failure") }
            assertNotNull(
                errorLog,
                "Janitor must log a critical boot failure error."
            )
        }


    @Test
    fun should_pipeNodeContextToHlcFactory_when_hydrating() = runEnv { scope ->
        // Given
        val nodeId = "node-to-end-all-nodes"

        val seededHlc =
            HlcTestFactory.create(
                ts = 1740787200000L,
                count = 2,
                nodeId = nodeId
            )
        nodeManager.seededContext = NodeContext(
            nodeId = nodeId,
            appVersion = 1,
            lastServerSyncTime = null,
            maxHlc = seededHlc,
            lastServerWatermark = null,
            lastLocalMutationTime = null
        )

        // When
        janitor.startupChecks()
        scope.advanceUntilIdle()

        // Then
        assertEquals(1, hlcFactory.hydrateCallCount)
        assertEquals(seededHlc, hlcFactory.lastHydratedHlc)
        assertEquals(nodeId, hlcFactory.lastHydratedNodeId)
    }

    @Test
    fun should_transitionToTransientFailure_when_startupThrowsTransientMochaException() =
        runEnv { scope ->
            // Given
            intentStore.failWith = MochaException.Transient.VaultBusy()

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            val currentState = bootUpdater.bootState.value
            assertTrue(
                currentState is BootState.TransientFailure,
                "Transient MochaException must route boot state to TransientFailure, but got: $currentState"
            )
        }

    @Test
    fun should_transitionToPersistentFailure_when_startupThrowsPersistentMochaException() =
        runEnv { scope ->
            // Given
            intentStore.failWith = MochaException.Persistent.DiskFull()

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            val currentState = bootUpdater.bootState.value
            assertTrue(
                currentState is BootState.CriticalFailure,
                "Persistent MochaException must route boot state to CriticalFailure, but got: $currentState"
            )
        }

    @Test
    fun should_wrapStartupChecks_inExecutionPolicyTag() = runEnv { scope ->
        // When
        janitor.startupChecks()
        scope.advanceUntilIdle()

        // Then
        val executedTags = executor.executionHistory
        assertTrue(
            executedTags.contains("[Startup Checks]"),
            "SyncJanitor startup logic must pass through executor with tag '[Startup Checks]'."
        )
    }

    // -----------------------------------------------------------
    // METADATA MAINTENANCE (SYNCINTENT)
    // -----------------------------------------------------------

    @Test
    fun should_clearStaleLocksAndResetIntentsToPending_when_staleIntentsExistOnStartup() =
        runEnv { scope ->
            // Given: Seed intent store with intents stuck in SYNCING with active syncIds (simulating process crash)
            val hlc1 = HlcTestFactory.create(ts = 100L, count = 0)
            val hlc2 = HlcTestFactory.create(ts = 200L, count = 0)

            intentStore.seedIntents(
                createTestSyncIntent(
                    hlc = hlc1,
                    status = SyncStatus.SYNCING,
                    syncId = "batch-stranded-1"
                ),
                createTestSyncIntent(
                    hlc = hlc2,
                    status = SyncStatus.SYNCING,
                    syncId = "batch-stranded-2"
                )
            )

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            val persistedIntents = intentStore.intents
            assertEquals(2, persistedIntents.size)
            assertTrue(
                persistedIntents.all { it.syncId == null && it.syncStatus == SyncStatus.PENDING },
                "Stranded locks must be cleared and reset to PENDING status."
            )

            // Verify audit log for lock cleanup
            val cleanupLog =
                writer.logs.find { it.message.contains("Cleared 2 stale") }
            assertNotNull(
                cleanupLog,
                "Janitor must log the number of cleared stale locks."
            )
        }

    @Test
    fun should_notLogIntentCleanup_when_noStaleIntentsExistOnStartup() = runEnv { scope ->
        // Given
        val cleanIntent = createTestSyncIntent(
            hlc = HlcTestFactory.create(),
            status = SyncStatus.PENDING
        )
        intentStore.seedIntents(cleanIntent)

        // When
        janitor.startupChecks()
        scope.advanceUntilIdle()

        // Then
        val cleanupLog = writer.logs.find { it.message.contains("stale mutation locks") }
        assertEquals(
            null,
            cleanupLog,
            "Janitor must skip the lock warning log when zero locks are cleared."
        )
    }

    // -----------------------------------------------------------
    // BLOB RECOVERY
    // -----------------------------------------------------------

    @Test
    fun should_commitStrandedBlob_when_matchingMetadataExistsInIntentStore() =
        runEnv { scope ->
            // Given
            val blobId = blobStore.stage(TestPayloads.defaultSource())

            intentStore.seedIntents(
                createTestSyncIntent(
                    hlc = HlcTestFactory.create(),
                    status = SyncStatus.PENDING,
                    overflowBlobId = blobId
                )
            )

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertTrue(
                blobStore.existsInCommitted(blobId),
                "Stranded blob with matching metadata must be committed."
            )
            assertFalse(
                blobStore.existsInPending(blobId),
                "Stranded blob with matching metadata must have been atomically moved out of pending."
            )

            val recoveryLog = writer.logs.find {
                it.message.contains("Recovering stranded blob $blobId")
            }
            assertNotNull(
                recoveryLog,
                "Janitor must log recovery when committing stranded blobs."
            )
        }

    @Test
    fun should_abortOrphanedBlob_when_noMatchingMetadataExistsInIntentStore() =
        runEnv { scope ->
            // Given
            val blobId = blobStore.stage(TestPayloads.defaultSource())

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then
            assertFalse(
                blobStore.existsInPending(blobId),
                "Orphaned blob with no persisted metadata must not exist in committed chamber."
            )

            val purgeLog = writer.logs.find {
                it.message.contains("Found orphaned pending blob $blobId")
            }
            assertNotNull(
                purgeLog,
                "Janitor must log purging when aborting orphaned blobs."
            )
        }

    @Test
    fun should_continueReconciliationAndComplete_when_individualBlobReconciliationThrows() =
        runEnv { scope ->
            // Given: Stage two distinct blobs
            val payloadA = byteArrayOf(0x01, 0x02)
            val payloadB = byteArrayOf(0x03, 0x04)
            val blobA = blobStore.stage(Buffer().apply { write(payloadA) })
            val blobB = blobStore.stage(Buffer().apply { write(payloadB) })
            // Seed intent metadata only for blobB
            intentStore.seedIntents(
                createTestSyncIntent(
                    hlc = HlcTestFactory.create(),
                    status = SyncStatus.PENDING,
                    overflowBlobId = blobB
                )
            )
            // Wire FakeIntentStore to throw when Janitor queries blobA
            intentStore.failOnBlobCheck(blobA)

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then 1: blobA encountered an error, which was caught and logged
            val blobAFailureLog = writer.logs.find {
                it.message.contains("Failed to reconcile individual blob: $blobA")
            }
            assertNotNull(
                blobAFailureLog,
                "Janitor must catch and log exception for blobA instead of crashing."
            )

            // Then 2: Loop continued and successfully committed blobB
            assertTrue(
                blobStore.existsInCommitted(blobB),
                "Janitor must proceed to reconcile blobB even if blobA threw an exception."
            )

            // Then 3: Full execution despite prior loop failure
            val completionLog = writer.logs.find {
                it.message.contains("Blob Reconciliation Complete")
            }
            assertNotNull(
                completionLog,
                "Janitor must proceed to clear incomplete staging and finish reconciliation."
            )
        }

    // -----------------------------------------------------------
    // RUNTIME INTENT MAINTENANCE
    // -----------------------------------------------------------

    @Test
    fun should_executeMaintenancePeriodically_onConfiguredInterval() = runEnv { scope ->
        // Given
        val maintenanceJob = janitor.startRuntimeMaintenance()

        // When: Trigger first cycle
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        val firstCycleLogs = writer.logs.count { it.message.contains("cycle finished") }
        assertEquals(
            1,
            firstCycleLogs,
            "Maintenance cycle execution interval not as expected."
        )

        // When: Trigger second cycle
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        val secondCycleLogs =
            writer.logs.count { it.message.contains("cycle finished") }
        assertEquals(
            firstCycleLogs + 1,
            secondCycleLogs,
            "Maintenance cycle must execute again on the next interval."
        )

        maintenanceJob.cancel()
    }

    @Test
    fun should_resetLeaseAndIncrementRetryCount_when_leaseIsStaleAndBelowThreshold() =
        runEnv { scope ->
            // Given: Seed intent with status Syncing and an expired lease
            val now = fakeClock.now().toEpochMilliseconds()
            val timeoutMs = config.staleThreshold.inWholeMilliseconds
            val staleTimestamp = now - (timeoutMs + 1000L)
            val initialRetryCount = config.retryThreshold - 3

            val intent = createTestSyncIntent(
                hlc = HlcTestFactory.create(),
                status = SyncStatus.SYNCING,
                leasedAt = staleTimestamp,
                retryCount = initialRetryCount,
                syncId = "testing"
            )
            intentStore.seedIntents(intent)

            // When
            val maintenanceJob = janitor.startRuntimeMaintenance()
            scope.advanceTimeBy(config.maintenanceInterval)
            scope.runCurrent()

            // Then
            val updatedIntent = intentStore.intents.firstOrNull()
            assertNotNull(updatedIntent)
            assertEquals(
                SyncStatus.PENDING,
                updatedIntent.syncStatus,
                "Stale lease must reset to PENDING state."
            )
            assertEquals(
                initialRetryCount + 1,
                updatedIntent.retryCount,
                "Retry count must increment by 1."
            )
            assertNull(
                updatedIntent.leasedAt,
                "leasedAt timestamp must be cleared on reset."
            )
            assertNull(
                updatedIntent.syncId,
                "syncId should be reset to null on lease reset."
            )

            maintenanceJob.cancel()
        }

    @Test
    fun should_escalateToQuarantine_when_staleLeaseReachesMaxRetries() = runEnv { scope ->
        // Given: Seed an intent with status syncing, and a retryCount requiring quarantine
        val now = fakeClock.now().toEpochMilliseconds()
        val timeoutMs = config.staleThreshold.inWholeMilliseconds
        val staleTimestamp = now - (timeoutMs + 1000L)
        val initialRetryCount = config.retryThreshold - 1

        val intent = createTestSyncIntent(
            hlc = HlcTestFactory.create(),
            status = SyncStatus.SYNCING,
            syncId = "test",
            leasedAt = staleTimestamp,
            retryCount = initialRetryCount
        )
        intentStore.seedIntents(intent)

        // When
        val maintenanceJob = janitor.startRuntimeMaintenance()
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        // Then
        val updatedIntent = intentStore.intents.firstOrNull()
        assertNotNull(updatedIntent)
        assertEquals(
            SyncStatus.QUARANTINED,
            updatedIntent.syncStatus,
            "Stale lease reaching max retries must escalate to quarantined."
        )
        assertEquals(config.retryThreshold, updatedIntent.retryCount)

        val quarantineLog = writer.logs.find {
            it.message.startsWith("Quarantined Intent [HLC: ${intent.hlc}]")
        }
        assertNotNull(quarantineLog, "Janitor must log quarantine escalation events.")

        maintenanceJob.cancel()
    }

    @Test
    fun should_ignoreActiveLeases_when_withinTimeoutWindow() = runEnv { scope ->
        // Given: Seed an intent within lease window
        val leaseStamp = fakeClock.now().toEpochMilliseconds()

        val intent = createTestSyncIntent(
            hlc = HlcTestFactory.create(),
            status = SyncStatus.SYNCING,
            syncId = "test",
            leasedAt = leaseStamp,
            retryCount = 1
        )
        intentStore.seedIntents(intent)

        // When
        val maintenanceJob = janitor.startRuntimeMaintenance()
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        // Then
        val updatedIntent = intentStore.intents.firstOrNull()
        assertNotNull(updatedIntent)
        assertEquals(
            SyncStatus.SYNCING,
            updatedIntent.syncStatus,
            "Active lease must remain at a syncing status."
        )
        assertEquals(
            1,
            updatedIntent.retryCount,
            "Retry count must not change for active leases."
        )
        assertEquals(
            leaseStamp,
            updatedIntent.leasedAt,
            "leasedAt must remain intact for active leases."
        )

        maintenanceJob.cancel()
    }

    @Test
    fun should_processVariousStateValidationsCorrectly_inSingleMaintenancePass() =
        runEnv { scope ->
            val now = fakeClock.now().toEpochMilliseconds()
            val timeoutMs = config.staleThreshold.inWholeMilliseconds
            val staleTimestamp = now - (timeoutMs + 1000L)
            val activeTimestamp = now - 1000L
            val hlcs = HlcTestFactory.concurrentSequence(3)

            val quarantineIntent = createTestSyncIntent(
                hlc = hlcs[0],
                status = SyncStatus.SYNCING,
                syncId = "quarantine-target",
                leasedAt = staleTimestamp,
                retryCount = config.retryThreshold - 1
            )
            val resetIntent = createTestSyncIntent(
                hlc = hlcs[1],
                status = SyncStatus.SYNCING,
                syncId = "reset-target",
                leasedAt = staleTimestamp,
                retryCount = 0
            )
            val activeIntent = createTestSyncIntent(
                hlc = hlcs[2],
                status = SyncStatus.SYNCING,
                syncId = "active-target",
                leasedAt = activeTimestamp,
                retryCount = 1
            )
            intentStore.seedIntents(quarantineIntent, resetIntent, activeIntent)

            // When
            val maintenanceJob = janitor.startRuntimeMaintenance()
            scope.advanceTimeBy(config.maintenanceInterval)
            scope.runCurrent()

            // Then 1
            val updatedQuarantine = intentStore.intents.find { it.hlc == hlcs[0] }
            assertNotNull(updatedQuarantine, "Quarantined intent must exist in store.")
            assertEquals(
                SyncStatus.QUARANTINED,
                updatedQuarantine.syncStatus,
                "Stale intent reaching threshold must transition to QUARANTINED."
            )
            assertEquals(config.retryThreshold, updatedQuarantine.retryCount)

            // Then 2
            val updatedReset = intentStore.intents.find { it.hlc == hlcs[1] }
            assertNotNull(updatedReset, "Reset intent must exist in store.")
            assertEquals(
                SyncStatus.PENDING,
                updatedReset.syncStatus,
                "Stale intent below threshold must reset to PENDING state."
            )
            assertEquals(
                1,
                updatedReset.retryCount,
                "Retry count must increment by 1."
            )
            assertNull(
                updatedReset.leasedAt,
                "LeasedAt timestamp must be cleared on reset."
            )

            // Then 3
            val updatedActive = intentStore.intents.find { it.hlc == hlcs[2] }
            assertNotNull(updatedActive, "Active intent must exist in store.")
            assertEquals(
                SyncStatus.SYNCING,
                updatedActive.syncStatus,
                "Active lease must remain in SYNCING state."
            )
            assertEquals(
                1,
                updatedActive.retryCount,
                "Active lease retry count must not change."
            )
            assertEquals(
                activeTimestamp,
                updatedActive.leasedAt,
                "Active lease timestamp must remain intact."
            )

            maintenanceJob.cancel()
        }


    // -----------------------------------------------------------
    // RUNTIME INTENT MAINTENANCE
    // -----------------------------------------------------------

    @Test
    fun should_triggerPruning_onMaintenanceTick() = runEnv { scope ->
        // Given
        val completedIntent = createTestSyncIntent(
            hlc = HlcTestFactory.create(),
            status = SyncStatus.SUCCESS
        )
        intentStore.seedIntents(completedIntent)

        // When
        val maintenanceJob = janitor.startRuntimeMaintenance()
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        // Then
        val pruneLog = writer.logs.any { it.message.contains("Prune Complete") }
        assertNotNull(
            pruneLog,
            "Pruning should be visibly logged during Janitors runtime maintenance."
        )
        val remaining = intentStore.intents.find { it.syncId == completedIntent.syncId }
        assertNull(remaining, "Completed intents must be pruned during maintenance tick.")

        maintenanceJob.cancel()
    }

    @Test
    fun should_continueMaintenanceLoop_when_pruningThrowsException() = runEnv { scope ->
        // Given
        intentStore.failWith = SQLiteException("database is locked")
        val prunableIntent = createTestSyncIntent(
            hlc = HlcTestFactory.create(),
            status = SyncStatus.SUCCESS
        )
        intentStore.seedIntents(prunableIntent)

        // When 1: Pruning fails due to exception
        val maintenanceJob = janitor.startRuntimeMaintenance()
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        val cycle1ErrorLog = writer.logs.find {
            it.message.contains("Intent pruning encountered error")
        }

        assertNotNull(
            cycle1ErrorLog,
            "Janitor must catch and log pruning exception on Cycle 1."
        )

        // When 2: Runtime maintenance restarts
        intentStore.failWith = null
        scope.advanceTimeBy(config.maintenanceInterval)
        scope.runCurrent()

        // Then
        val completionLogs = writer.logs.count {
            it.message.contains("Runtime maintenance cycle finished")
        }
        val pruneLogs =
            writer.logs.any { it.message.contains("Prune Complete | Total: 1") }
        assertEquals(
            completionLogs,
            2,
            "Maintenance loop must remain active and execute Cycle 2 despite Cycle 1 failure."
        )
        assertNotNull(
            pruneLogs,
            "Janitor runtime maintenance should handle error and delegate single prune."
        )

        maintenanceJob.cancel()
    }

}