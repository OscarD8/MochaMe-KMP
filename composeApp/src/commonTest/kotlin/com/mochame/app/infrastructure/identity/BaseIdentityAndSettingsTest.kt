package com.mochame.app.infrastructure.identity

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.RoomSettingsStore
import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.entities.GlobalSettingsEntity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.IdentityTestEnvironment
import com.mochame.app.di.modules.AppModules
import com.mochame.app.utils.utilizeTestScope
import dev.mokkery.answering.calls
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
import kotlin.test.assertNotNull

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

        val db: MochaDatabase = get { parametersOf(testDispatcher) }
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
    val databaseErrors = listOf(
        SQLiteException("database is locked"),
        SQLiteException("database is locked")
    )

    @Test
    fun should_retry_twice_then_successfully_write_to_db() = runTestWrapper {
        // Arrange: store provided a mocked DAO and a real executor
        val mockDao = mock<SettingsDao>()
        val storeWithMock = RoomSettingsStore(mockDao, executor)

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
        val dbResult = settingsDao.getGlobalSettings()
        assertNotNull(dbResult, "The real DB was never reached!")
        assertEquals("verified", dbResult.nodeId, "The real DB was never updated!")

        val recoveryLog = writer.logs.find { it.message.contains("3 attempts") }
        assertNotNull(recoveryLog, "Log expected for three attempts made.")
    }
}