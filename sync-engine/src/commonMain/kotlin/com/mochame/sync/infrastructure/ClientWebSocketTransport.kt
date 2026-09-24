package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.spi.network.WireFrame
import com.mochame.sync.spi.network.SendResult
import com.mochame.sync.spi.network.SyncTransport
import com.mochame.sync.spi.network.WireFrameFactory
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.utils.interfaces.TimeUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Single(binds = [SyncTransport::class])
internal class ClientWebSocketTransport(
    @AppBackgroundScope private val backgroundScope: CoroutineScope,
    private val nodeManager: NodeContextManager,
    private val timeUtils: TimeUtils,
    logger: Logger
) : SyncTransport {

    private val logger =
        logger.withTags(LogTags.Layer.TRANSPORT, LogTags.Domain.SYNC, "ClSock")

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 15.seconds
        }
    }

    private data class ConnectionEndpoint(
        val host: String,
        val port: Int,
        val groupId: String,
        val nodeId: String
    )

    private val lifecycleMutex = Mutex()

    @Volatile
    private var endpoint: ConnectionEndpoint? = null
    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null

    @Volatile
    private var inboundDeltaHandler: (suspend (watermark: Long, payload: ByteArray) -> Unit)? = null
    @Volatile
    private var inboundAckHandler: (suspend (batchId: Long, watermark: Long) -> Unit)? = null
    @Volatile
    private var onConnectedListener: (suspend () -> Unit)? = null
    @Volatile
    private var onDisconnectedListener: (suspend () -> Unit)? = null

    private var connectionJob: Job? = null
    private var pauseDebounceJob: Job? = null
    private var isPaused: Boolean = false

    override val isConnected: Boolean
        get() = activeSession?.isActive == true

    override fun setOnConnectedListener(onConnected: suspend () -> Unit) {
        this.onConnectedListener = onConnected
    }

    override fun setOnDisconnectedListener(onDisconnected: suspend () -> Unit) {
        this.onDisconnectedListener = onDisconnected
    }

    override fun registerInboundDeltaHandler(onReceived: suspend (Long, ByteArray) -> Unit) {
        this.inboundDeltaHandler = onReceived
    }

    override fun registerInboundAckHandler(onAck: suspend (Long, Long) -> Unit) {
        this.inboundAckHandler = onAck
    }

    override suspend fun connect(
        host: String,
        port: Int,
        groupId: String
    ) {
        lifecycleMutex.withLock {
            val nodeId = nodeManager.getNodeId() ?: error("Node Context is not initialized.")
            val newEndpoint = ConnectionEndpoint(host, port, groupId, nodeId.value.toString())
            if (endpoint == newEndpoint && connectionJob?.isActive == true && !isPaused) {
                return@withLock
            }

            endpoint = newEndpoint
            isPaused = false
            pauseDebounceJob?.cancel()
            pauseDebounceJob = null

            teardownActiveConnectionLocked()
            startConnectionLoopLocked()
        }
    }

    override fun pause() {
        backgroundScope.launch {
            lifecycleMutex.withLock {
                if (isPaused || pauseDebounceJob?.isActive == true) return@withLock

                pauseDebounceJob = backgroundScope.launch {
                    delay(1.5.seconds)
                    lifecycleMutex.withLock {
                        isPaused = true
                        pauseDebounceJob = null
                        logger.i { "Grace period expired. Terminating WebSocket connection." }
                        teardownActiveConnectionLocked()
                    }
                }
            }
        }
    }

    override fun resume() {
        backgroundScope.launch {
            lifecycleMutex.withLock {
                if (pauseDebounceJob?.isActive == true) {
                    logger.i { "Resumed within grace window. Preserving existing connection." }
                    pauseDebounceJob?.cancel()
                    pauseDebounceJob = null
                    isPaused = false
                    return@withLock
                }

                if (!isPaused || endpoint == null) return@withLock
                logger.i { "Resuming transport: Re-establishing socket connection." }
                isPaused = false
                startConnectionLoopLocked()
            }
        }
    }

    private suspend fun teardownActiveConnectionLocked() {
        val session = activeSession
        val job = connectionJob
        activeSession = null
        connectionJob = null

        if (session != null) {
            try {
                onDisconnectedListener?.invoke()
                withTimeoutOrNull(500.milliseconds) {
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "App backgrounded"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.v(e) { "Socket close handshake aborted or already closed: ${e.message}" }
            }
        }

        job?.cancelAndJoin()
    }

    private fun startConnectionLoopLocked() {
        val target = endpoint ?: return
        if (connectionJob?.isActive == true) return

        connectionJob = backgroundScope.launch {
            while (isActive && !isPaused) {
                try {
                    runSingleSession(target)

                    if (isActive && !isPaused) {
                        logger.i { "WebSocket channel closed remotely. Attempting reconnection in 30s..." }
                        delay(30.seconds)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: MochaException.Persistent) {
                    logger.e(e) { "Terminating connection until manual trigger." }
                    break
                } catch (e: Exception) {
                    logger.w(e) { "[${e::class.simpleName}] ${e.message}. Retrying in 10s..." }
                    delay(10.seconds)
                } finally {
                    activeSession = null
                }
            }
        }
    }

    override suspend fun send(batchId: Long, payload: ByteArray): SendResult {
        val session = activeSession ?: return SendResult.NoConnection

        return try {
            val frame = WireFrameFactory.client(batchId, payload)
            session.send(Frame.Binary(fin = true, data = frame))
            SendResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failure(e)
        }
    }

    private suspend fun runSingleSession(target: ConnectionEndpoint) {
        val currentWatermark = nodeManager.getLastInboundWatermark() ?: 0L

        client.webSocket(
            host = target.host,
            port = target.port,
            path = "/sync/${target.groupId}/${target.nodeId}?since=$currentWatermark"
        ) {
            activeSession = this
            try {
                logger.i { "WebSocket connected to ${target.host}:${target.port} (Since Watermark: $currentWatermark)" }

                for (frame in incoming) {
                    if (frame is Frame.Binary) {
                        dispatchWireFrame(frame.readBytes())
                    }
                }
            } finally {
                activeSession = null
            }
        }
    }

    /**
     * Does not catch illegal state exceptions. If the payload was corrupt, current behavior is to terminate
     * the session immediately for debugging, and to allow reconnection to trigger a backfill and retry.
     */
    private suspend fun DefaultClientWebSocketSession.dispatchWireFrame(bytes: ByteArray) {
        when (val wireFrame = WireFrameFactory.unwrap(bytes)) {
            is WireFrame.BackfillComplete -> {
                logger.i { "Backfill complete. Triggering outbound pipeline flush." }
                backgroundScope.launch {
                    try {
                        onConnectedListener?.invoke()
                    } catch (e: Exception) {
                        logger.e(e) { "Outbound queue flush failed on ready" }
                    }
                }
            }

            is WireFrame.Ack -> {
                inboundAckHandler?.invoke(wireFrame.batchId, wireFrame.watermark)
            }

            is WireFrame.Delta -> {
                try {
                    inboundDeltaHandler?.invoke(wireFrame.watermark, wireFrame.payload)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.e(e) { "Inbound processing failed at watermark ${wireFrame.watermark}. Closing socket." }
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Inbound ingestion error"))
                }
            }

            is WireFrame.ClientSubmit -> {
                logger.w { "Device received an unexpected client submit frame [BatchId: ${wireFrame.batchId}] [Size: ${wireFrame.payload.size}]" }
            }
        }
    }
}

