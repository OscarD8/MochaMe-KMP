package com.mochame.node

import com.mochame.node.database.NodeContextMicroSchema
import com.mochame.node.database.NodeContextMicroSchemaConstructor
import com.mochame.node.di.NodeContextTestApp
import com.mochame.node.di.NodeContextTestEnv
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------

private inline fun runEnv(crossinline block: suspend NodeContextTestEnv.(TestScope) -> Unit) =
    runPersistenceEnvironment<NodeContextMicroSchema, NodeContextTestEnv>(
        constructor = NodeContextMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<NodeContextTestApp>()) },
        block = block
    )


class NodeContextManagerTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun yay_or_nay() = runEnv {
        // Given
        val context = manager.getOrEstablishContext()

        // When
        val id = manager.getNodeId()

        // Then
        assertNotNull(id)
    }

    @Test
    fun should_preserve_node_id_when_updating_app_version_and_getting_context() = runEnv {
        // Arrange:
        manager.getOrEstablishContext(1)
        val originalId = manager.getNodeId()

        // Act:
        manager.setAppVersion(2)

        // Assert:
        val updatedState = manager.getOrEstablishContext()
        assertEquals(
            originalId,
            updatedState.nodeId,
            "The Node ID was corrupted during a version update"
        )
        assertEquals(
            2,
            updatedState.appVersion,
            "Node app version was not as expected after simple upsert."
        )
    }

    @Test
    fun should_initialize_single_id_when_async_polls_to_manager() =
        runEnv { scope ->
            // Arrange: manager awaiting multithreaded assault
            val threads = 12
            val gate = CompletableDeferred(Unit)

            val results = List(threads) {
                scope.async(Dispatchers.Default) {
                    gate.await()
                    manager.getOrEstablishContext()
                }
            }

            // Act: FIRE
            gate.complete(Unit)
            val completedResult = results.awaitAll()
            val persistedId = completedResult.last().nodeId

            // Assert: only a single distinct node.
            assertEquals(
                1,
                completedResult.distinct().size,
                "NodeManager failed to provide a single stable ID under structured concurrency."
            )

            assertEquals(
                persistedId,
                completedResult.first().nodeId,
                "NodeManager should not have updated ID from first call."
            )
        }

}