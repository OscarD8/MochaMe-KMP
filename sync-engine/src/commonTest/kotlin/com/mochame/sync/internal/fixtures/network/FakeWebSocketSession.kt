package com.mochame.sync.internal.fixtures.network

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext


class FakeWebSocketSession(parentContext: CoroutineContext) : WebSocketSession {
    private val sessionJob = Job(parentContext[Job])
    override val coroutineContext: CoroutineContext = parentContext + sessionJob

    val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
    val outgoingChannel = Channel<Frame>(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> get() = incomingChannel

    // Fault injection for TR-WS-09
    var sendException: Throwable? = null

    // Minimal proxy: drops automated Ktor pings and supports sendException
    @OptIn(InternalCoroutinesApi::class)
    override val outgoing: SendChannel<Frame> = object : SendChannel<Frame> by outgoingChannel {
        override suspend fun send(element: Frame) {
            when (element) {
                is Frame.Ping -> {
                    incomingChannel.send(Frame.Pong(element.data))
                    return
                }
                is Frame.Pong -> return
                else -> {
                    sendException?.let { throw it }
                    outgoingChannel.send(element)
                }
            }
        }

        override fun trySend(element: Frame): ChannelResult<Unit> {
            return when (element) {
                is Frame.Ping -> {
                    incomingChannel.trySend(Frame.Pong(element.data))
                    ChannelResult.success(Unit)
                }
                is Frame.Pong -> ChannelResult.success(Unit)
                else -> outgoingChannel.trySend(element)
            }
        }
    }

    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var maxFrameSize: Long = Long.MAX_VALUE
    override var masking: Boolean = false

    override suspend fun flush() = Unit

    suspend fun close(reason: CloseReason) {
        outgoingChannel.send(Frame.Close(reason))
        outgoingChannel.close()
        sessionJob.cancel()
    }

    @Deprecated("Use close() instead")
    override fun terminate() {
        incomingChannel.close()
        outgoingChannel.close()
        sessionJob.cancel()
    }
}