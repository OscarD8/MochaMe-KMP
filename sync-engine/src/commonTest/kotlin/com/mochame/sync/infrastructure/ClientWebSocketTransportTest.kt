package com.mochame.sync.infrastructure

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.infrastructure.ClientWebSocketTransportTestEnv
import com.mochame.sync.di.infrastructure.TransportTestModule
import io.ktor.websocket.Frame
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.koin.core.KoinApplication
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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

@DelicateCoroutinesApi
@ExperimentalCoroutinesApi
class ClientWebSocketTransportTest : MochaPlatformTest() {

    // -------------------------------------------------------------------------
    // Endpoint
    // -------------------------------------------------------------------------

    @Test
    fun should_connectAndDisconnect_correctly() = runEnv { scope ->
        // Given
        val context = nodeManager.getOrEstablishContext()
        val watermark = context.lastInboundWatermark ?: 0L

        // When: Connect
        transport.connect(host = "localhost", port = 8080, groupId = "bene_gesserit")
        val request = awaitHandshake()
        scope.runCurrent()

        assertEquals(
            "/sync/bene_gesserit/${context.nodeId}?since=${watermark}",
            request.url.encodedPathAndQuery
        )
        assertTrue(transport.isConnected)

        // When: Teardown
        teardown()
        scope.advanceUntilIdle()

        val session = assertNotNull(currentSession)
        val frames = session.awaitFrames(1)
        assertEquals(1, frames.size)
        assertIs<Frame.Close>(frames.first())
        assertFalse(transport.isConnected)
        assertTrue(session.outgoing.isClosedForSend)
    }

    @Test
    fun should_throwIllegalStateException_whenNodeIdMissing() = runEnv { scope ->
        val exception = assertFailsWith<IllegalStateException> {
            transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        }

        assertEquals("Node Context is not initialized.", exception.message)

        scope.runCurrent()
        assertTrue(engine.handshakeRequests.isEmpty)
        assertTrue(engine.sessionChannel.isEmpty)
        assertFalse(transport.isConnected)
    }

    @Test
    fun should_skipTeardownAndAvoidDuplicateConnections_whenInvokedTwiceWithIdenticalParameters() =
        runEnv { scope ->
            nodeManager.getOrEstablishContext()

            // Given: An initial connection is established
            transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
            scope.runCurrent()

            awaitHandshake()
            val firstSession = awaitSession()
            scope.runCurrent()

            assertTrue(transport.isConnected)
            assertTrue(firstSession.coroutineContext.isActive)

            // When: connect() is invoked again with identical parameters while active
            transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
            scope.runCurrent()

            // Then:
            assertTrue(engine.handshakeRequests.isEmpty)
            assertTrue(engine.sessionChannel.isEmpty)
            assertTrue(firstSession.coroutineContext.isActive)
            assertSame(firstSession, currentSession)
            assertTrue(transport.isConnected)

            teardown()
        }

    // -------------------------------------------------------------------------
    // Debounce
    // -------------------------------------------------------------------------

    @Test
    fun should_cancelTeardownAndRetainConnection_whenResumedWithinGracePeriod() = runEnv { scope ->
        // Given: An active connection is established
        nodeManager.getOrEstablishContext()
        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        scope.runCurrent()

        awaitHandshake()
        val session = awaitSession()
        scope.runCurrent()

        assertTrue(transport.isConnected)
        assertTrue(session.coroutineContext.isActive)

        // When: pause() is called and time advances 500ms (< grace period)
        transport.pause()
        scope.advanceTimeBy(500.milliseconds)
        scope.runCurrent()

        // Then: The connection remains open during the grace period
        assertTrue(transport.isConnected)
        assertTrue(session.coroutineContext.isActive)

        // When: resume() is called within the grace window
        transport.resume()
        scope.runCurrent()

        // Advance time past the original mark to ensure the debounce job was canceled
        scope.advanceTimeBy(10.seconds)
        scope.runCurrent()

        // Then: Teardown was completely bypassed; original session is intact
        assertTrue(transport.isConnected)
        assertTrue(session.coroutineContext.isActive)
        assertTrue(session.drainSentFrames().isEmpty())
        assertFalse(session.outgoing.isClosedForSend)
        assertSame(session, currentSession)

        teardown()
    }

    @Test
    fun should_reestablishSession_whenResumedAfterGracePeriodExpired() = runEnv { scope ->
        val context = nodeManager.getOrEstablishContext()
        val watermark = context.lastInboundWatermark ?: 0L

        // Given: Transport is paused and allowed to expire into a torn-down state
        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        scope.runCurrent()

        awaitHandshake()
        val firstSession = awaitSession()
        scope.runCurrent()
        assertTrue(transport.isConnected)

        transport.pause()
        scope.advanceTimeBy(10.seconds)
        scope.runCurrent()

        assertFalse(transport.isConnected)
        assertFalse(firstSession.coroutineContext.isActive)
        val frames = firstSession.drainSentFrames()
        assertEquals(1, frames.size)
        assertIs<Frame.Close>(frames.first())

        // When: resume() is called after full expiration
        transport.resume()
        scope.runCurrent()

        // Then: Connection loop restarts and establishes a new session
        val secondHandshake = awaitHandshake()
        val secondSession = awaitSession()
        scope.runCurrent()

        assertTrue(transport.isConnected)
        assertTrue(secondSession.coroutineContext.isActive)
        assertNotSame(firstSession, secondSession)
        assertEquals(
            "/sync/team_alpha/${context.nodeId}?since=${watermark}",
            secondHandshake.url.encodedPathAndQuery
        )

        teardown()
    }

}