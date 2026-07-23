@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.orchestration

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.support.MochaPlatformTest
import com.mochame.support.TestHlcFactory
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.models.HLC
import com.mochame.sync.di.janitor.JanitorTestApp
import com.mochame.sync.di.janitor.JanitorTestEnv
import com.mochame.sync.fakes.createTestSyncIntent
import com.mochame.sync.spi.node.NodeContext
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend JanitorTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<JanitorTestEnv>(
        koinSetup = { includes(koinConfiguration<JanitorTestApp>()) },
        block = block
    )

private val testPayloadBytes = byteArrayOf(0x53, 0x54, 0x52, 0x41, 0x4E, 0x44, 0x45, 0x44)


@ExperimentalCoroutinesApi
class SyncJanitorTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // BOOT LIFECYCLE / STATE GUARDS (HLC/BOOT)
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
            val failure = MochaException.Persistent.ClockSkew(5000L, "seconds")
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
            nodeManager.simulatedDelay = 6000.milliseconds

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

                assertTrue(failureState is BootState.CriticalFailure)
                assertTrue(failureState.exception is MochaException.Persistent.BootLockout)
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
        val baseTestTime = 1740787200000L
        val nodeId = "node-to-end-all-nodes"
        fakeClock.setTime(1740787200000L)

        val seededHlc =
            TestHlcFactory.create(
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

    // -----------------------------------------------------------
    // METADATA MAINTENANCE (SYNCINTENT)
    // -----------------------------------------------------------

    @Test
    fun should_clearStaleLocksAndResetIntentsToPending_when_staleIntentsExistOnStartup() =
        runEnv { scope ->
            // Given: Seed intent store with intents stuck in SYNCING with active syncIds (simulating process crash)
            val hlc1 = TestHlcFactory.create(ts = 100L, count = 0)
            val hlc2 = TestHlcFactory.create(ts = 200L, count = 0)

            val lockedIntent1 = createTestSyncIntent(
                hlc = hlc1,
                candidateKey = "key-1",
                status = SyncStatus.SYNCING,
                syncId = "batch-stranded-1"
            )

            val lockedIntent2 = createTestSyncIntent(
                hlc = hlc2,
                candidateKey = "key-2",
                status = SyncStatus.SYNCING,
                syncId = "batch-stranded-2"
            )

            intentStore.seedIntents(lockedIntent1, lockedIntent2)

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
                writer.logs.find { it.message.contains("Cleared 2 stale mutation locks") }
            assertNotNull(
                cleanupLog,
                "Janitor must log the number of cleared stale locks."
            )
        }

    // -----------------------------------------------------------
    // METADATA MAINTENANCE (SYNCINTENT)
    // -----------------------------------------------------------

    @Test
    fun should_notLogIntentCleanup_when_noStaleIntentsExistOnStartup() = runEnv { scope ->
        // Given
        val cleanIntent = createTestSyncIntent(
            hlc = TestHlcFactory.create(),
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
            val payload = Buffer().apply { write(testPayloadBytes) }
            val blobId = blobStore.stage(payload)

            val intent = createTestSyncIntent(
                hlc = TestHlcFactory.create(),
                status = SyncStatus.PENDING,
                overflowBlobId = blobId
            )
            intentStore.seedIntents(intent)

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
            // Given: Stage raw byte payload into pending chamber without seeding intentStore
            val payload = Buffer().apply { write(testPayloadBytes) }
            val blobId = blobStore.stage(payload)

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then: Blob is aborted and purged from disk
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

    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------

    @Test
    fun should_logNewNodeId_when_newInstall() =
        runEnv { scope ->
            // Given
            nodeManager.forcedNextNodeId = "fake-node"

            // When
            janitor.startupChecks()
            scope.advanceUntilIdle()

            // Then assert the "Ear" heard the "Mouth" (says Gemini)
            val seedingMessage =
                writer.logs.find { it.message.contains("fake-node") }
            assertNotNull(seedingMessage, "The success log should have been recorded!")
        }

}


/*
        // Arrange: store provided a mocked DAO and a real executor

        everySuspend { mockDao.updateNodeId(any()) } sequentially {
            throws(SQLiteException("BUSY"))
            throws(SQLiteException("BUSY"))

            // Third call: actually call the real database
            calls { (newId: String) -> settingsDao.updateNodeId(newId) }
        }

        // Act:
        storeWithMock.saveNodeId("verified")

        // Assert
        // Verify the mock was poked 3 times
        verifySuspend(VerifyMode.order) {
            mockDao.updateNodeId("verified")
            mockDao.updateNodeId("verified")
            mockDao.updateNodeId("verified")
        }

        // Verify: real database now contains the data
        val dbResult = settingsDao.getNodeIdentity()
        assertNotNull(dbResult, "The real DB was never reached!")
        assertEquals("verified", dbResult.nodeId, "The real DB was never updated!")

        val recoveryLog = writer.logs.find { it.message.contains("3 attempts") }
        assertNotNull(recoveryLog, "Log expected for three attempts made.")

 */
