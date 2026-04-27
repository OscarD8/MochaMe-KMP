package com.mochame.app.infrastructure.identity

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.data.local.room.MochaDbOld
import com.mochame.app.data.local.room.entities.GlobalSettingsEntity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.IdentityTestEnvironment
import com.mochame.app.di.modules.AppModules
import com.mochame.app.utils.utilizeTestScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
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

@ExperimentalKermitApi
abstract class BaseIdentityAndSettingsTest : KoinTest {

    // -----------------------------------------------------------
    // TEST COMPONENTS
    // -----------------------------------------------------------
    abstract val platformModules: List<Module>

    private val testModules = listOf(
        AppModules.identityModule,
        CoreTestModules.identityTestEnvironmentModule,
        CoreTestModules.testLoggingModule(minSeverity = Severity.Verbose),
    )

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun setup() {
        startKoin {
            allowOverride(true)
            modules(testModules + platformModules)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    fun runTestWrapper(
        block: suspend IdentityTestEnvironment.(TestScope) -> Unit
    ) = runTest {
        val testDispatcher = this.utilizeTestScope()

        val db: MochaDbOld = get { parametersOf(testDispatcher) }
        val env: IdentityTestEnvironment by inject()

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
    fun should_retrieve_existing_id_when_get_or_create_called() = runTestWrapper {
        // Arrange
        val existingId = "node"
        settingsDao.insert(GlobalSettingsEntity(
            id = 1,
            nodeId = existingId,
            lastAppVersion = 1,
        ))

        // Act
        val result = manager.getOrCreateNodeId()

        // Assert
        assertEquals(existingId, result, "Manager replaced the ID.")
    }

    @Test
    fun should_preserve_node_id_when_updating_app_version() = runTestWrapper {
        // 1. Arrange:
        val originalId = manager.getOrCreateNodeId()

        // 2. Act:
        settingsDao.updateAppVersion(2)

        // 3. Assert:
        val finalSettings = settingsDao.getGlobalSettings()
        assertEquals(originalId, finalSettings?.nodeId, "The Node ID was corrupted during a version update!")
        assertEquals(2, finalSettings?.lastAppVersion)
    }

    // -----------------------------------------------------------
    // CONCURRENCY
    // -----------------------------------------------------------
    @Test
    fun should_initialize_single_id_when_async_polls_to_manager() =
        runTestWrapper { scope ->
            // Arrange: Empty store awaiting multithreaded assault
            val threads = 30
            val gate = CompletableDeferred(Unit)

            val results = List(threads) {
                scope.async(Dispatchers.Default) {
                    gate.await()
                    manager.getOrCreateNodeId()
                }
            }

            // Act: FIRE and wait
            gate.complete(Unit)
            val completedResult = results.awaitAll().distinct()

            // Assert: only a single distinct node.
            assertEquals(
                1,
                completedResult.size,
                "IdentityManager failed to provide a single stable ID under structured concurrency."
            )
        }


    // -----------------------------------------------------------
    // ROOM SETTINGS STORE IMPLEMENTATION
    // -----------------------------------------------------------


}