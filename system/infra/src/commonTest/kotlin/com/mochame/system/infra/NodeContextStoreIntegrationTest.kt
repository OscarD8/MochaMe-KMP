package com.mochame.system.infra

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.system.infra.database.NodeContextMicroSchema
import com.mochame.system.infra.database.NodeContextMicroSchemaConstructor
import com.mochame.system.infra.di.NodeContextStoreIntegrationTestApp
import com.mochame.system.infra.di.NodeContextStoreTestEnv
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------

private inline fun runEnv(crossinline block: suspend NodeContextStoreTestEnv.(TestScope) -> Unit) =
    runPersistenceEnvironment<NodeContextMicroSchema, NodeContextStoreTestEnv>(
        constructor = NodeContextMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<NodeContextStoreIntegrationTestApp>()) },
        block = block
    )

private const val templateId = "node1"


class NodeContextStoreTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun yay_or_nay() = runEnv {
        // Given
        store.saveNodeId(templateId)

        // Then
        assertEquals(templateId, store.getNodeId(), "Store didn't return what we saved.")
    }

    @Test
    fun should_preserve_node_id_when_updating_app_version() = runEnv {
        // 1. Arrange:
        store.saveNodeId(templateId)

        // 2. Act:
        store.setVersion(2)

        // 3. Assert:
        val updatedState = store.getContext()
        assertEquals(
            templateId,
            updatedState?.nodeId,
            "The Node ID was corrupted during a version update :("
        )
        assertEquals(
            2,
            updatedState?.baselineVersion,
            "Node app version was not as expected after simple upsert."
        )
    }

}