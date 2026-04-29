package com.mochame.system.orchestrator.test


import com.mochame.system.orchestrator.test.di.IdentityManagerUnitTestApp
import com.mochame.system.orchestrator.test.di.IdentityTestEnvironment
import com.mochame.support.runUnitEnvironment
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals


private inline fun runUnitTest(crossinline block: suspend IdentityTestEnvironment.(TestScope) -> Unit) =
    runUnitEnvironment<IdentityTestEnvironment>(
        koinSetup = { includes(koinConfiguration<IdentityManagerUnitTestApp>()) },
        block = block
    )


class IdentityManagerUnitTest {
    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun should_retrieve_existing_id_when_get_or_create_called() = runUnitTest {
        // Arrange
        val existingId = idGenerator.nextId()
        store.saveNodeId(existingId)

        // Act
        val result = manager.getOrCreateNodeId()

        // Assert
        assertEquals(
            existingId,
            result,
            "Manager should not have replaced an existing ID."
        )
    }

}

//    @Test
//    fun should_preserve_node_id_when_updating_app_version() = runTestWrapper {
//        // 1. Arrange:
//        val originalId = manager.nextId()
//
//        // 2. Act:
//        settingsDao.updateAppVersion(2)
//
//        // 3. Assert:
//        val finalSettings = settingsDao.getGlobalSettings()
//        assertEquals(
//            originalId,
//            finalSettings?.nodeId,
//            "The Node ID was corrupted during a version update!"
//        )
//        assertEquals(2, finalSettings?.lastAppVersion)
//    }
//
//    // -----------------------------------------------------------
//    // CONCURRENCY
//    // -----------------------------------------------------------
//    @Test
//    fun should_initialize_single_id_when_async_polls_to_manager() =
//        runTestWrapper { scope ->
//            // Arrange: Empty store awaiting multithreaded assault
//            val threads = 30
//            val gate = CompletableDeferred(Unit)
//
//            val results = List(threads) {
//                scope.async(Dispatchers.Default) {
//                    gate.await()
//                    manager.nextId()
//                }
//            }
//
//            // Act: FIRE and wait
//            gate.complete(Unit)
//            val completedResult = results.awaitAll().distinct()
//
//            // Assert: only a single distinct node.
//            assertEquals(
//                1,
//                completedResult.size,
//                "IdentityManager failed to provide a single stable ID under structured concurrency."
//            )
//        }
//
//}