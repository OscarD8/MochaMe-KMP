@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.test.orchestration

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.contract.boot.BootState
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.data.entities.SyncModuleStateEntity
import com.mochame.sync.test.database.SyncMicroSchema
import com.mochame.sync.test.database.SyncMicroSchemaConstructor
import com.mochame.sync.test.di.janitor.JanitorTestApp
import com.mochame.sync.test.di.janitor.JanitorTestEnv
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

        assertEquals(MochaModuleContext.allFeatureModules.size, metadataStore.getMetadataCount())
    }

    // -----------------------------------------------------------
    // FAILURE PATH
    // -----------------------------------------------------------
    @Test
    fun should_enter_critical_failure_when_last_hlc_is_from_the_future() =
        runEnv { scope ->
            // Arrange
            // Seed a Future HLC (2040-01-01...)
            val futureHlc = "2209032000000:0:node-1"

            metadataDao.upsertMetadata(
                SyncModuleStateEntity(
                    module = MochaModuleContext.Type.UNRECOGNIZED_FALLBACK.moduleName,
                    moduleMaxHlc = futureHlc,
                    lastServerSyncTime = 1000L,
                    lastLocalMutationTime = 1000L
                )
            )

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

                assertTrue(finalState.throwable is MochaException.Persistent.ClockSkew)

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

                scope.advanceTimeBy(5001L)
                expectNoEvents() // -- should not have hit internal timeout

                scope.advanceTimeBy(15_001L)
                val failureState = awaitItem()

                assertTrue(failureState is BootState.CriticalFailure)
                assertTrue(failureState.throwable is MochaException.Persistent.BootLockout)
            }

            janitorMutex.unlock()
        }

    @Test
    fun should_report_transient_failure_when_boot_hydration_times_out() =
        runEnv { scope ->
            // Arrange: lock NodeContextManager mutex
            manager.mutex.lock()

            // Act: launch the janitor
            janitor.startupChecks()

            scope.runCurrent() // -- janitor stalls on hydration, locked from fetching deviceId
            assertEquals(BootState.Initializing, bootUpdater.bootState.value)

            // -- Fast-forward virtual time past the 5-second timeout
            scope.advanceTimeBy(5001)

            // Assert: Verify the Janitor caught the timeout and failed the boot.
            val finalState = bootUpdater.bootState.value
            assertTrue(
                finalState is BootState.TransientFailure,
                "Janitor should have failed on timeout! Got $finalState.."
            )
            assertTrue(finalState.throwable is MochaException.Transient.BootTimeout)
        }

    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------
    @Test
    fun should_log_correct_seeding_count_when_new_install() = runEnv { scope ->
        val count = MochaModuleContext.allFeatureModules.size

        janitor.startupChecks()
        scope.advanceUntilIdle()

        // Assert the "Ear" heard the "Mouth" (says Gemini)
        val seedingMessage =
            writer.logs.find { it.message.contains("Seeded $count missing") }
        assertNotNull(seedingMessage, "The success log should have been recorded!")
        assertEquals(Severity.Info, seedingMessage.severity)
    }

}

// -----------------------------------------------------------
// TODO
// -----------------------------------------------------------

// No longer valid, shifted execution policy to global executors to prevent
// nested execution policies and improve retry logic of top level functions...
// will use this logic elsewhere
//    @Test
//    fun should_retry_twice_then_successfully_write_to_db() = runTestWrapper {
//        // Arrange: store provided a mocked DAO and a real executor
//        val mockDao = mock<SettingsDao>()
//        val storeWithMock = RoomSettingsStore(mockDao)
//
//        everySuspend { mockDao.updateNodeId(any()) } sequentially {
//            throws(SQLiteException("BUSY"))
//            throws(SQLiteException("BUSY"))
//
//            // Third call: actually call the real database
//            calls { (newId: String) -> settingsDao.updateNodeId(newId) }
//        }
//
//        // Act:
//        storeWithMock.saveNodeId("verified")
//
//        // Assert
//        // Verify the mock was poked 3 times
//        verifySuspend(VerifyMode.order) {
//            mockDao.updateNodeId("verified")
//            mockDao.updateNodeId("verified")
//            mockDao.updateNodeId("verified")
//        }
//
//        // Verify: real database now contains the data
//        val dbResult = settingsDao.getNodeIdentity()
//        assertNotNull(dbResult, "The real DB was never reached!")
//        assertEquals("verified", dbResult.nodeId, "The real DB was never updated!")
//
//        val recoveryLog = writer.logs.find { it.message.contains("3 attempts") }
//        assertNotNull(recoveryLog, "Log expected for three attempts made.")
//    }