package com.mochame.node

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.node.data.NodeContextMicroSchema
import com.mochame.node.data.NodeContextMicroSchemaConstructor
import com.mochame.node.data.nodeTableName
import com.mochame.node.di.NodeContextTestApp
import com.mochame.node.di.NodeContextTestEnv
import com.mochame.support.MochaPlatformTest
import com.mochame.support.TestHlcFactory
import com.mochame.support.getPhysicalRowCount
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.spi.node.NodeContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    // CONTEXT INTEGRITY / MAPPING
    // -----------------------------------------------------------
    @Test
    fun should_establishCleanDefaultContext_when_databaseIsEmpty() = runEnv {
        // Given
        val targetBaseVersion = 3
        // When
        val establishedContext = manager.getOrEstablishContext(targetBaseVersion)
        // Then
        assertNotNull(establishedContext.nodeId)
        assertNotNull(establishedContext.createdAt)
        assertEquals(targetBaseVersion, establishedContext.appVersion)
        assertNull(establishedContext.maxHlc)
        assertNull(establishedContext.lastServerWatermark)
        assertNull(establishedContext.lastServerSyncTime)
        assertNull(establishedContext.lastLocalMutationTime)

        // Safety check at database boundary
        assertEquals(1, db.getPhysicalRowCount(nodeTableName))
    }

    @Test
    fun should_preserveAllPopulatedFields_when_mappingRoundTripExecutes() = runEnv {
        // Given
        val expectedHlc =
            TestHlcFactory.create(ts = 15000L, count = 4, nodeId = "node-alpha")
        val populatedDomainContext = NodeContext(
            nodeId = "node-alpha",
            appVersion = 12,
            createdAt = 1000L,
            lastServerWatermark = "server-sync-token-xyz",
            maxHlc = expectedHlc,
            lastServerSyncTime = 8888L,
            lastLocalMutationTime = 9999L
        )

        // When
        manager.overwriteNodeContext(populatedDomainContext)
        val fetchedContext = manager.getOrEstablishContext()

        // Then
        assertEquals(populatedDomainContext.nodeId, fetchedContext.nodeId)
        assertEquals(populatedDomainContext.appVersion, fetchedContext.appVersion)
        assertEquals(populatedDomainContext.createdAt, fetchedContext.createdAt)
        assertEquals(
            populatedDomainContext.lastServerWatermark,
            fetchedContext.lastServerWatermark
        )
        assertEquals(populatedDomainContext.maxHlc, fetchedContext.maxHlc)
        assertEquals(
            populatedDomainContext.lastServerSyncTime,
            fetchedContext.lastServerSyncTime
        )
        assertEquals(
            populatedDomainContext.lastLocalMutationTime,
            fetchedContext.lastLocalMutationTime
        )
    }

    // -----------------------------------------------------------
    // CONCURRENCY
    // -----------------------------------------------------------

    @Test
    fun should_initialize_single_id_when_async_polls_to_manager() =
        runEnv { scope ->
            // Given
            val threads = 8
            val gate = CompletableDeferred(Unit)

            val workerDeferreds = List(threads) {
                scope.async(Dispatchers.Default) {
                    gate.await()
                    manager.getOrEstablishContext()
                }
            }

            // When
            gate.complete(Unit)
            val completedResult = workerDeferreds.awaitAll()
            val expectedNodeId = completedResult.first().nodeId

            // Then
            // Assert every single concurrent caller received the exact same identity instance
            completedResult.forEach { context ->
                assertEquals(
                    expectedNodeId,
                    context.nodeId,
                    "Multi-threaded initialization returned mismatched IDs."
                )
            }

            // Verify physical storage invariance directly
            assertEquals(
                1,
                db.getPhysicalRowCount(nodeTableName),
                "Database must physically contain exactly one configuration row."
            )
        }

    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------

    @OptIn(ExperimentalKermitApi::class)
    @Test
    fun should_logDebugMessage_when_daoReturnsZeroRowsUpdated() = runEnv {
        // Given
        val hlc = TestHlcFactory.create(ts = 1000L, count = 1)
        manager.updateHlcFloor(hlc)

        // When
        manager.updateHlcFloor(hlc)

        // Then
        val updateIgnoredLog = writer.logs.any { it.message.contains("ignored") }
        assertTrue(updateIgnoredLog)
    }

}