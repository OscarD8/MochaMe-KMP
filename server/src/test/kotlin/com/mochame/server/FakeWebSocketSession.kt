package com.mochame.server

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext

/**
 * When testing session teardown: RelayManager and DatabaseActor call sender.session.close(...) directly on the session. That bypasses outboundChannel and goes straight to session.outgoing.
 * Your awaitCloseReason() will catch this immediately without needing an active egress loop.   When testing live deltas or ACKs in isolation: The actor pushes ACKs into senderHandle.outboundChannel. If your unit test does not launch an outboundJob, those ACKs will sit in handle.outboundChannel, not in fakeSession.outgoing. In that unit test, assert directly against handle.outboundChannel.tryReceive().
 */
class FakeWebSocketSession(
    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Unconfined
) : WebSocketSession {

    val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<Frame> = incomingChannel

    private val backingOutgoing = Channel<Frame>(Channel.UNLIMITED)

    // Latches the close reason asynchronously without race conditions
    private val closeReasonDeferred = CompletableDeferred<CloseReason>()

    var capturedCloseReason: CloseReason? = null
        private set

    // Intercepts Ktor's outgoing.send(Frame.Close) calls
    override val outgoing: SendChannel<Frame> = object : SendChannel<Frame> by backingOutgoing {
        override suspend fun send(element: Frame) {
            intercept(element)
            backingOutgoing.send(element)
        }

        override fun close(cause: Throwable?): Boolean {
            val wasClosed = backingOutgoing.close(cause)
            if (wasClosed && !closeReasonDeferred.isCompleted) {
                if (cause != null) {
                    closeReasonDeferred.completeExceptionally(cause)
                } else {
                    val fallback = CloseReason(CloseReason.Codes.NORMAL, "Outgoing channel closed")
                    capturedCloseReason = fallback
                    closeReasonDeferred.complete(fallback)
                }
            }
            return wasClosed
        }

        override fun trySend(element: Frame): ChannelResult<Unit> {
            intercept(element)
            return backingOutgoing.trySend(element)
        }

        private fun intercept(frame: Frame) {
            if (frame is Frame.Close) {
                val reason = frame.readReason() ?: CloseReason(CloseReason.Codes.NORMAL, "Closed")
                capturedCloseReason = reason
                closeReasonDeferred.complete(reason)
            }
        }
    }

    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var maxFrameSize: Long = Long.MAX_VALUE
    override var masking: Boolean = false

    override suspend fun flush() {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun terminate() {
        // Fallback: If terminate() is called without sending a Frame.Close first
        if (!closeReasonDeferred.isCompleted) {
            val fallback = CloseReason(CloseReason.Codes.NORMAL, "Terminated without close frame")
            capturedCloseReason = fallback
            closeReasonDeferred.complete(fallback)
        }
        coroutineContext.cancel()
    }

    /**
     * Suspends until session.close(...) which launches a coroutine finishes pushing its Close frame.
     */
    suspend fun awaitCloseReason(): CloseReason = closeReasonDeferred.await()

    /**
     * Drains sent application frames for assertion checks.
     */
    fun drainSentFrames(): List<Frame> {
        val frames = mutableListOf<Frame>()
        while (true) {
            val frame = backingOutgoing.tryReceive().getOrNull() ?: break
            frames.add(frame)
        }
        return frames
    }
}