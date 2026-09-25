package com.mochame.sync.infrastructure

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.infrastructure.ClientWebSocketTransportTestEnv
import com.mochame.sync.di.infrastructure.TransportTestModule
import com.mochame.sync.spi.node.NodeContext
import com.mochame.utils.fixtures.TestNodeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.koin.core.KoinApplication
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds


private inline fun runEnv(
    bindTestScope: Boolean = true,
    crossinline koinSetup: KoinApplication.() -> Unit = {},
    crossinline block: suspend ClientWebSocketTransportTestEnv.(TestScope) -> Unit
) = runUnitEnvironment<ClientWebSocketTransportTestEnv>(
    bindTestScope = bindTestScope,
    koinSetup = {
        modules(TransportTestModule::class)
        koinSetup()
    },
    block = block
)

@ExperimentalCoroutinesApi
class ClientWebSocketTransportTest : MochaPlatformTest() {

    @Test
    fun should_connectAndFormatHandshakeUri_correctly() = runEnv { scope ->
        // Given
        nodeManager.overwriteNodeContext(
            NodeContext(
                nodeId = TestNodeId.A,
                appVersion = 1,
                lastInboundWatermark = 42L
            )
        )

        // When
        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        scope.runCurrent()
        awaitHandshake()
        scope.runCurrent()

        // Then
        assertTrue(transport.isConnected)

        transport.pause()
        scope.advanceTimeBy(2.seconds)
        scope.runCurrent()
    }
}