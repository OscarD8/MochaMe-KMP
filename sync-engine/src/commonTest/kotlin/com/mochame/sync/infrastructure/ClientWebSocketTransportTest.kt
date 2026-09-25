package com.mochame.sync.infrastructure

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.di.infrastructure.ClientWebSocketTransportTestEnv
import com.mochame.sync.di.infrastructure.TransportTestModule
import com.mochame.sync.spi.network.SendResult
import com.mochame.sync.spi.network.WireFrame
import com.mochame.sync.spi.network.WireFrameFactory
import com.mochame.sync.spi.network.encode
import com.mochame.utils.fixtures.TestPayloads
import io.ktor.client.request.invoke
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import org.koin.core.KoinApplication
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
        val session = awaitSession()
        scope.runCurrent()

        assertEquals(
            "/sync/bene_gesserit/${context.nodeId}?since=${watermark}",
            request.url.encodedPathAndQuery
        )
        assertTrue(transport.isConnected)

        // When: Teardown
        teardown()
        scope.advanceUntilIdle()

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

        // Then:
        assertTrue(transport.isConnected)
        assertTrue(session.coroutineContext.isActive)

        // When: resume() is called within the grace window
        transport.resume()
        scope.runCurrent()
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

    // -------------------------------------------------------------------------
    // Outbound
    // -------------------------------------------------------------------------

    @Test
    fun should_dispatchBinaryClientSubmitFrameAndReturnSuccess_whenSessionIsActive() =
        runEnv { scope ->
            // Given: An active connection is established
            nodeManager.getOrEstablishContext()
            transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")

            awaitHandshake()
            val session = awaitSession()
            scope.runCurrent()
            assertTrue(transport.isConnected)

            val batchId = 101L
            val payload = TestPayloads.DEFAULT
            val expectedWireBytes = WireFrameFactory.client(batchId, payload)

            // When:
            val result = transport.send(batchId, payload)

            // Then: Returns Success and dispatches matching binary ClientSubmit frame
            assertEquals(SendResult.Success, result)

            val outgoingFrame = session.outgoingChannel.receive()
            assertTrue(outgoingFrame is Frame.Binary)
            assertContentEquals(expectedWireBytes, outgoingFrame.readBytes())

            val unwrapped = WireFrameFactory.unwrap(outgoingFrame.readBytes())
            assertTrue(unwrapped is WireFrame.ClientSubmit)
            assertEquals(batchId, unwrapped.batchId)
            assertContentEquals(payload, unwrapped.payload)

            teardown()
        }

    @Test
    fun should_returnNoConnectionImmediately_whenSessionIsInactiveOrNull() = runEnv {
        nodeManager.getOrEstablishContext()
        assertFalse(transport.isConnected)

        val result = transport.send(batchId = 1L, payload = TestPayloads.DEFAULT)

        assertEquals(SendResult.NoConnection, result)
    }

    @Test
    fun should_returnSendResultFailure_onParsingError() = runEnv { scope ->
        // Given: Active session
        nodeManager.getOrEstablishContext()
        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        awaitHandshake()
        awaitSession()
        scope.runCurrent()
        assertTrue(transport.isConnected)

        // When:
        val result = transport.send(batchId = 1L, payload = ByteArray(0))

        // Then:
        assertIs<SendResult.Failure>(result)
        assertIs<IllegalArgumentException>(result.cause)

        teardown()
    }

    @Test
    fun should_returnNoConnection_whenSessionCancelledConcurrently() = runEnv { scope ->
        // Given: Active connection
        nodeManager.getOrEstablishContext()
        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        awaitHandshake()
        val session = awaitSession()
        scope.runCurrent()
        assertTrue(transport.isConnected)

        // When: send() is invoked after or during session teardown
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Network drop"))
        val result = transport.send(batchId = 202L, payload = TestPayloads.DEFAULT)

        // Then:
        assertEquals(SendResult.NoConnection, result)

        teardown()
    }

    // -------------------------------------------------------------------------
    // Inbound
    // -------------------------------------------------------------------------

    @Test
    fun should_invokeInboundAckHandler_whenAckFrameReceived() = runEnv { scope ->
        // Given: An active session with a registered ack handler
        nodeManager.getOrEstablishContext()
        val ackDeferred = CompletableDeferred<Pair<Long, Long>>()
        transport.registerInboundAckHandler { batchId, watermark ->
            ackDeferred.complete(batchId to watermark)
        }

        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        awaitHandshake()
        val session = awaitSession()
        scope.runCurrent()
        assertTrue(transport.isConnected)

        // When: Inbound Ack frame (10, 50) is emitted to the client
        session.incomingChannel.send(
            Frame.Binary(fin = true, data = WireFrame.Ack(10L, 1L).encode())
        )
        scope.runCurrent()

        // Then: inboundAckHandler is invoked with matching values
        assertTrue(ackDeferred.isCompleted)
        val (batchId, watermark) = ackDeferred.await()
        assertEquals(10L, batchId)
        assertEquals(1L, watermark)

        teardown()
    }

    @Test
    fun should_invokeInboundDeltaHandler_whenDeltaFrameReceived() = runEnv { scope ->
        // Given: An active session with a registered delta handler
        nodeManager.getOrEstablishContext()
        val deltaDeferred = CompletableDeferred<Pair<Long, ByteArray>>()
        transport.registerInboundDeltaHandler { watermark, payload ->
            deltaDeferred.complete(watermark to payload)
        }

        transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
        awaitHandshake()
        val session = awaitSession()
        scope.runCurrent()
        assertTrue(transport.isConnected)

        // When:
        session.incomingChannel.send(
            Frame.Binary(fin = true, WireFrame.Delta(1L, TestPayloads.DEFAULT).encode())
        )
        scope.runCurrent()

        // Then: inboundDeltaHandler is invoked with matching watermark and bytes
        assertTrue(deltaDeferred.isCompleted)
        val (watermark, payload) = deltaDeferred.await()
        assertEquals(1L, watermark)
        assertContentEquals(TestPayloads.DEFAULT, payload)

        teardown()
    }

    @Test
    fun should_closeSocketWithTryAgainLater_whenInboundDeltaHandlerThrowsException() =
        runEnv { scope ->
            // Given: An active session where delta processing encounters an ingestion error
            nodeManager.getOrEstablishContext()
            transport.registerInboundDeltaHandler { _, _ ->
                throw IllegalStateException("Blargian Snagglebeast")
            }

            transport.connect(host = "localhost", port = 8080, groupId = "team_alpha")
            awaitHandshake()
            val session = awaitSession()
            scope.runCurrent()
            assertTrue(transport.isConnected)

            // When: A delta frame arrives and handler throws
            session.incomingChannel.send(
                Frame.Binary(fin = true, WireFrame.Delta(1L, TestPayloads.DEFAULT).encode())
            )

            // Then: Socket is closed with TRY_AGAIN_LATER
            val closeFrame = session.outgoingChannel.receive() as? Frame.Close
            assertNotNull(closeFrame)
            val reason = closeFrame.readReason()
            assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, reason?.code)
            assertEquals("Inbound ingestion error", reason?.message)
            assertFalse(transport.isConnected)
            assertTrue(session.sessionJob.isCancelled)

            teardown()
        }


}