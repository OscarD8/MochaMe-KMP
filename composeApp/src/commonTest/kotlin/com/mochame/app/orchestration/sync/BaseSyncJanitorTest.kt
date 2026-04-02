package com.mochame.app.orchestration.sync

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.RoomSettingsStore
import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.entity.GlobalSettingsEntity
import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.JanitorTestEnvironment
import com.mochame.app.di.modules.AppModules
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.domain.system.sync.utils.SyncStatus
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.utils.utilizeTestScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalKermitApi
@ExperimentalCoroutinesApi
abstract class BaseSyncJanitorTest : KoinTest {

    // -----------------------------------------------------------
    // MODULES
    // -----------------------------------------------------------
    abstract val platformTestModules: List<Module>
    private val coreTestModules: List<Module> = listOf(
        AppModules.janitorSetupModules,
        CoreTestModules.janitorTestEnvironmentModule
    )

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun start_koin_context() {
        startKoin {
            allowOverride(true)
            modules(coreTestModules + platformTestModules)
        }
    }

    @AfterTest
    fun stop_koin_context() {
        stopKoin()
    }

    fun runTestWrapper(block: suspend JanitorTestEnvironment.(TestScope) -> Unit) = runTest {
        val testDispatcher = this.utilizeTestScope()

        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val env: JanitorTestEnvironment by inject()

        try {
            env.block(this)
        } finally {
            env.writer.reset()
            db.close()
        }
    }

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun yay_or_nay() = runTestWrapper { scope ->
        janitor.startupChecks()

        scope.advanceUntilIdle()

        assertEquals(MochaModule.entries.size, metadataStore.getMetadataCount())
    }

    // -----------------------------------------------------------
    // FAILURE PATH
    // -----------------------------------------------------------
    @Test
    fun should_enter_critical_failure_when_last_hlc_is_from_the_future() = runTestWrapper {
        // Arrange
        // Seed a "Future" HLC (2040-01-01...)
        val futureHlc = "2209032000000:0:node-1"

        metadataDao.upsertMetadata(
            SyncMetadataEntity(
                module = MochaModule.BIO,
                localMaxHlc = futureHlc,
                syncStatus = SyncStatus.IDLE,
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

    @Test
    fun should_set_transient_failure_when_vault_is_busy() = runTestWrapper { scope ->
        // Arrange
        // We manually lock the Janitor's internal mutex to simulate contention.
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
            assertTrue(failureState.throwable is MochaException.Persistent.BootTimeout)
        }

        janitorMutex.unlock()
    }

    // -----------------------------------------------------------
    // CONCURRENCY
    // -----------------------------------------------------------
    @Test
    fun should_report_critical_failure_when_boot_hydration_times_out() = runTestWrapper { scope ->
        // Arrange: lock IdentityManager node determination
        identityMutex.lock()

        // Act: hijack the lock and then launch the janitor
        janitor.startupChecks()

        scope.runCurrent() // -- janitor stalls on hydration, locked from fetching deviceId
        assertEquals(BootState.Initializing, bootUpdater.bootState.value)

        // -- Fast-forward virtual time past the 5-second timeout
        scope.advanceTimeBy(5001)

        // Assert: Verify the Janitor caught the timeout and failed the boot.
        val finalState = bootUpdater.bootState.value
        assertTrue(finalState is BootState.CriticalFailure, "Janitor should have failed on timeout! Got $finalState..")
        assertTrue(finalState.throwable is MochaException.Persistent.BootTimeout)
    }

    // -----------------------------------------------------------
    // STRESS
    // -----------------------------------------------------------


    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------
    @Test
    fun should_log_correct_seeding_count_when_new_install() = runTestWrapper { scope ->
        val count = MochaModule.entries.size

        janitor.startupChecks()
        scope.advanceUntilIdle()

        // Assert the "Ear" heard the "Mouth" (says Gemini)
        val seedingMessage = writer.logs.find { it.message.contains("Seeded $count missing") }
        assertNotNull(seedingMessage, "The success log should have been recorded!")
        assertEquals(Severity.Info, seedingMessage.severity)
    }
}