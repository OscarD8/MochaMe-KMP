package com.mochame.system.orchestrator.test.nodeManager


import com.mochame.contract.fixtures.fakeId
import com.mochame.support.MochaPlatformTest
import com.mochame.system.orchestrator.test.di.NodeManagerUnitTestApp
import com.mochame.system.orchestrator.test.di.NodeManagerTestEnv
import com.mochame.support.runUnitEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

//   -----------------------------------------------------------
//   SUT Environment
//   -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend NodeManagerTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<NodeManagerTestEnv>(
        koinSetup = { includes(koinConfiguration<NodeManagerUnitTestApp>()) },
        block = block
    )


class NodeContextManagerUnitTest : MochaPlatformTest() {

    //   -----------------------------------------------------------
    //   SUCCESS
    //   -----------------------------------------------------------
    @Test
    fun should_retrieve_existing_id_when_get_or_create_called() = runEnv {
        // Arrange
        val existingId = fakeIdGen.nextId()
        fakeStore.saveNodeId(existingId)

        // Act
        val result = manager.getOrCreateNodeId()

        // Assert
        assertEquals(
            1.fakeId,
            result,
            "Manager should not have replaced an existing ID."
        )
    }

//     -----------------------------------------------------------
//     CONCURRENCY
//     -----------------------------------------------------------
    @Test
    fun should_initialize_single_id_when_async_polls_to_manager() =
        runEnv { scope ->
            // Arrange: Empty store awaiting multithreaded assault
            val threads = 30
            val gate = CompletableDeferred(Unit)

            val results = List(threads) {
                scope.async(Dispatchers.IO) {
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
                "NodeManager failed to provide a single stable ID under structured concurrency."
            )

            assertEquals(
                1.fakeId,
                completedResult.first(),
                "NodeManager should not have updated ID from first call."
            )
        }


}