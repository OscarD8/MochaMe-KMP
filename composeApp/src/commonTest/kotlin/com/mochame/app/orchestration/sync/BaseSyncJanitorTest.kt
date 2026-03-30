package com.mochame.app.orchestration.sync

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.JanitorTestEnvironment
import com.mochame.app.di.modules.AppModules
import com.mochame.app.domain.system.exceptions.MochaException
import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.infrastructure.system.boot.BootState
import com.mochame.app.utils.establishTestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
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

    fun runTestWrapper(block: suspend TestScope.(JanitorTestEnvironment) -> Unit) = runTest {
        val testDispatcher = this.establishTestScope()

        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val env: JanitorTestEnvironment by inject()

        try {
            this.block(env)
        } finally {
            env.writer.reset()
            db.close()
        }
    }

    @Test
    fun yay_or_nay() = runTestWrapper { tools ->
        tools.janitor.startupChecks()

        advanceUntilIdle()

        assertEquals(3, tools.metadataStore.getMetadataCount())
    }

    @Test
    fun should_log_correct_seeding_count_when_new_install() = runTestWrapper { tools ->
        val count = MochaModule.entries.size

        tools.janitor.startupChecks()
        advanceUntilIdle()

        // Assert the "Ear" heard the "Mouth" (says Gemini)
        val seedingMessage = tools.writer.logs.find { it.message.contains("Seeded $count missing") }
        assertNotNull(seedingMessage, "The success log should have been recorded!")
        assertEquals(Severity.Info, seedingMessage.severity)
    }

    @Test
    fun should_set_transient_failure_when_vault_is_busy() = runTestWrapper { env ->
        // Arrange
        // We manually lock the Janitor's internal mutex to simulate contention.
        val janitorMutex = get<Mutex>(named("JanitorMutex"))
        janitorMutex.lock()

        // Act
        env.janitor.startupChecks()

        // Use Turbine to observe the StateFlow transitions
        env.statusProvider.bootState.test {
            // Assert
            // 1. Initially it should be Idle
            assertEquals(BootState.Idle, awaitItem())

            // 2. Because the launch is async, we await the next state
            val failureState = awaitItem()
            assertTrue(failureState is BootState.TransientFailure)

            val error = failureState.throwable
            assertTrue(error is MochaException.Transient.VaultBusy)

            cancelAndIgnoreRemainingEvents()
        }

        janitorMutex.unlock()
    }

}