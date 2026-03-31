package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.TestTag
import com.mochame.app.di.modules.AppModules
import com.mochame.app.utils.establishTestScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalKermitApi
class IdentityManagerFakesTest : KoinTest {

    // -----------------------------------------------------------
    // TEST COMPONENTS
    // -----------------------------------------------------------
    private val testModules = listOf(
        AppModules.identityModule,
        CoreTestModules.fakeLatentSettingsStore,
        CoreTestModules.testLoggingModule(TestTag.CORE),
    )

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun setup() {
        startKoin {
            allowOverride(true)
            modules(testModules)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    fun runTestWrapper(
        block: suspend TestScope.(IdentityManager, TestLogWriter) -> Unit
    ) = runTest {
        this.establishTestScope()

        val writer: TestLogWriter by inject()
        val identityManager: IdentityManager by inject()

        try {
            this.block(identityManager, writer)
        } finally {
            writer.reset()
        }
    }

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------


    // -----------------------------------------------------------
    // CONCURRENCY
    // -----------------------------------------------------------

    // This test is only valid if  yields are added to the fake.
    @Test
    fun should_provoke_race_condition_returning_multiple_node_ids() =
        runTestWrapper { manager, writer ->

            // Arrange: Start with an empty store (handled by In-Memory DB setup)
            val concurrentCalls = 100

            // Act: Hammer the manager from 100 separate coroutines
            val results = (1..concurrentCalls).map {
                async { manager.getOrCreateNodeId() }
            }.awaitAll()

            // Assert:
            // In a broken system, some threads will read 'null' before others finish saving.
            // They will generate their own UUIDs and overwrite each other.
            val distinctIds = results.distinct()

            if (distinctIds.size > 1) {
                println("❌ Race Condition Proven! Generated ${distinctIds.size} different IDs.")
            }

            // This assertion will FAIL on your current code, proving the bug.
            assertEquals(
                1,
                distinctIds.size,
                "IdentityManager failed to provide a single stable ID under load."
            )

        }

}