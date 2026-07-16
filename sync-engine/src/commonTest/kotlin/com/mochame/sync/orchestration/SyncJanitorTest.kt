@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.orchestration

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.api.models.HLC
import com.mochame.sync.di.janitor.JanitorTestApp
import com.mochame.sync.di.janitor.JanitorTestEnv
import com.mochame.sync.data.SyncMicroSchema
import com.mochame.sync.data.SyncMicroSchemaConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend JanitorTestEnv.(TestScope) -> Unit) =
    runPersistenceEnvironment<SyncMicroSchema, JanitorTestEnv>(
        constructor = SyncMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<JanitorTestApp>()) },
        block = block
    )


@ExperimentalCoroutinesApi
class SyncJanitorTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun yay_or_nay() = runEnv { scope ->
        janitor.startupChecks()

        scope.advanceUntilIdle()

        assertNotNull(nodeManager.getOrEstablishContext())
    }

    // -----------------------------------------------------------
    // FAILURE PATH
    // -----------------------------------------------------------
    @Test
    fun should_enter_critical_failure_when_last_hlc_is_from_the_future() =
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

    // -----------------------------------------------------------
    // CONCURRENCY
    // -----------------------------------------------------------
    @Test
    fun should_set_critical_failure_when_janitors_own_lock_is_busy() =
        runEnv { scope ->
            // Arrange
            janitorMutex.lock()

            // Act
            janitor.startupChecks()

            // Assert
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
    fun should_report_transient_failure_when_boot_hydration_times_out() =
        runEnv { scope ->
            // Arrange - lock NodeContextManager mutex
            nodeManager.mutex.lock()

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

    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------
    @Test
    fun should_log_correct_seeding_count_for_new_node_when_new_install() =
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
