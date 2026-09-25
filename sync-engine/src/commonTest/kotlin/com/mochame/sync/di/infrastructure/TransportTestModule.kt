package com.mochame.sync.di.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.test.TestLoggerModule
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.sync.infrastructure.ClientWebSocketTransport
import com.mochame.sync.internal.fixtures.network.FakeWebSocketEngine
import com.mochame.sync.internal.fixtures.network.FakeWebSocketSession
import com.mochame.sync.spi.network.SyncTransport
import com.mochame.sync.spi.network.WireFrame
import com.mochame.sync.spi.network.encode
import com.mochame.sync.spi.node.NodeContextManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        FixturesNodeModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.infrastructure")
internal class TransportTestModule {

    @Single(binds = [HttpClientEngine::class, FakeWebSocketEngine::class])
    fun provideFakeWebSocketEngine(
        @AppBackgroundScope backgroundScope: CoroutineScope
    ): FakeWebSocketEngine = FakeWebSocketEngine(
        onConnect = { _ ->
            FakeWebSocketSession(backgroundScope.coroutineContext)
        },
        parentContext = backgroundScope.coroutineContext,
        config = HttpClientEngineConfig()
    )

    @Single(binds = [SyncTransport::class, ClientWebSocketTransport::class])
    fun provideClientWebSocketTransport(
        @AppBackgroundScope backgroundScope: CoroutineScope,
        nodeManager: NodeContextManager,
        engine: HttpClientEngine,
        logger: Logger
    ): ClientWebSocketTransport = ClientWebSocketTransport(
        backgroundScope = backgroundScope,
        nodeManager = nodeManager,
        engine = engine,
        logger = logger
    )
}

@Factory
internal class ClientWebSocketTransportTestEnv(
    val transport: ClientWebSocketTransport,
    val engine: FakeWebSocketEngine,
    val nodeManager: FakeNodeContextManager,
    val logger: Logger
) {
    var currentSession: FakeWebSocketSession? = null
        private set

    suspend fun awaitHandshake(): HttpRequestData =
        engine.handshakeRequests.receive()

    suspend fun awaitSession(): FakeWebSocketSession =
        engine.sessionChannel.receive().also { currentSession = it }

    suspend fun emitToClient(frame: WireFrame) {
        val session = currentSession ?: awaitSession()
        session.incomingChannel.send(
            Frame.Binary(fin = true, data = frame.encode())
        )
    }

    suspend fun closeRemotely(
        reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "Remote disconnect")
    ) {
        val session = currentSession ?: awaitSession()
        session.close(reason)
    }
}