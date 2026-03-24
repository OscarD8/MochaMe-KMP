package com.mochame.app.domain.sync

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.core.HlcFactory
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.di.DispatcherProvider
import com.mochame.app.di.TestDispatcherProvider
import com.mochame.app.domain.repository.sync.SyncJanitor
import com.mochame.app.domain.system.BootStatusProvider
import com.mochame.app.modules.CoreTestModules
import com.mochame.app.modules.JanitorTestEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.inject
import org.koin.test.verify.verify
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue



abstract class BaseSyncJanitorTest : KoinTest{

    // -----------------------------------------------------------
    // MODULES
    // -----------------------------------------------------------
    abstract val platformTestModules: List<Module>
    private val coreTestModules: List<Module> = listOf(
        CoreTestModules.syncTestSystemModule,
        CoreTestModules.janitorModule
    )

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun start_koin_context() {
        startKoin {
            modules(coreTestModules + platformTestModules)
        }
    }

    @AfterTest
    fun stop_koin_context() {
        stopKoin()
    }

    fun runTestWrapper(block: suspend TestScope.(JanitorTestEnvironment) -> Unit) = runTest {
        val testDispatcher = this.coroutineContext[ContinuationInterceptor] as TestDispatcher

        loadKoinModules(module {
            factory<DispatcherProvider> { TestDispatcherProvider(testDispatcher) }
            factory<CoroutineScope>(named("AppScope")) { this@runTest }
        })

        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val env: JanitorTestEnvironment by inject()

        try {
            this.block(env)
        } finally {
            env.writer.reset()
            db.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun yay_or_nay() = runTestWrapper { tools ->
        tools.janitor.startupChecks()

        advanceUntilIdle()

        assertEquals(3, tools.metaDataDao.getMetadataCount())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun janitor_should_log_success_after_seeding() = runTestWrapper { tools ->
        tools.janitor.startupChecks()
        advanceUntilIdle()

        // Assert the "Ear" heard the "Mouth" (says Gemini)
        val seedingMessage = tools.writer.logs.find { it.message.contains("Successfully seeded") }
        assertNotNull(seedingMessage, "The success log should have been recorded!")
        assertEquals(Severity.Info, seedingMessage.severity)
    }
}