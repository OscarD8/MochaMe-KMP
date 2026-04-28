package com.mochame.orchestrator.test


import com.mochame.contract.di.AppScope
import com.mochame.logger.test.TestLoggerModule
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.support.runUnitEnvironment
import com.mochame.contract.identity.IdGenerator
import com.mochame.logic.IdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals


@Factory
data class IdentityTestEnvironment(
    val manager: IdentityManager,
    val fakeStore: GlobalMetadataStore,
    val idGenerator: IdGenerator,
    @AppScope val scope: CoroutineScope
)

@KoinApplication(modules = [TestLoggerModule::class])
object IdManagerTestApp

inline fun runIdManagerTest(crossinline block: suspend IdentityTestEnvironment.(TestScope) -> Unit) =
    runUnitEnvironment<IdentityTestEnvironment>(
        koinSetup = { includes(koinConfiguration<IdManagerTestApp>()) },
        block = block
    )


class IdentityManagerTest {
    private val fakeId = "fake-id-1"

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun should_retrieve_existing_id_when_get_or_create_called() = runIdManagerTest {
        // Arrange
        val existingId = "node"
        fakeStore.saveNodeId(fakeId)

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
        assertEquals(
            originalId,
            finalSettings?.nodeId,
            "The Node ID was corrupted during a version update!"
        )
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

}