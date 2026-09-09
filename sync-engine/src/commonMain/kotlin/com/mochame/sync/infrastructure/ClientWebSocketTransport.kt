package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.spi.network.SyncTransport
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Single(binds = [SyncTransport::class])
internal class ClientWebSocketTransport(
    @AppBackgroundScope private val backgroundScope: CoroutineScope,
    logger: Logger
) : SyncTransport {

    private val logger =
        logger.withTags(LogTags.Layer.TRANSPORT, LogTags.Domain.SYNC, "MrKtor")

    private val client = HttpClient(CIO) {
        install(WebSockets)
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
    private var inboundHandler: (suspend (ByteArray) -> Unit)? = null

    @Volatile
    private var onConnectedListener: (suspend () -> Unit)? = null

    private var connectionJob: Job? = null
    private var pauseDebounceJob: Job? = null
    private var isPaused: Boolean = false

    override fun setOnConnectedListener(onConnected: suspend () -> Unit) {
        this.onConnectedListener = onConnected
    }

    override fun registerInboundHandler(onReceived: suspend (ByteArray) -> Unit) {
        this.inboundHandler = onReceived
    }

    override suspend fun connect(host: String, port: Int, groupId: String, nodeId: String) {
        lifecycleMutex.withLock {
            val newEndpoint = ConnectionEndpoint(host, port, groupId, nodeId)
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
                    delay(1500.milliseconds)
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
                withTimeoutOrNull(500.milliseconds) {
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "App backgrounded"))
                }
            } catch (_: Exception) {
                // Suppress transport-level teardown exceptions
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
                    client.webSocket(
                        host = target.host,
                        port = target.port,
                        path = "/sync/${target.groupId}/${target.nodeId}"
                    ) {
                        activeSession = this
                        try {
                            logger.i { "WebSocket connected to ${target.host}:${target.port} (Group: ${target.groupId}, Node: ${target.nodeId})" }

                            backgroundScope.launch {
                                try {
                                    // HOLD UNTIL THE WATERMARK ESTABLISHMENT IS CLEARED
                                    onConnectedListener?.invoke()
                                } catch (e: Exception) {
                                    logger.e(e) { "Outbound queue flush failed on connection ready" }
                                }
                            }

                            for (frame in incoming) {
                                if (frame is Frame.Binary) {
                                    try {
                                        inboundHandler?.invoke(frame.readBytes())
                                    } catch (e: Exception) {
                                        logger.e(e) { "Failed to process inbound binary frame. Preserving connection." }
                                    }
                                }
                            }
                        } finally {
                            activeSession = null
                        }
                    }
                } catch (e: Exception) {
                    activeSession = null
                    if (e is CancellationException) break

                    logger.w(e) { "WebSocket disconnected: [${e::class.simpleName}] ${e.message}. Retrying in 3s..." }
                    delay(3.seconds)
                }
            }
        }
    }

    override suspend fun send(payload: ByteArray): Boolean {
        val session = activeSession ?: return false
        return try {
            session.send(Frame.Binary(fin = true, data = payload))
            true
        } catch (e: Exception) {
            logger.w(e) { "Failed to transmit binary frame over active WebSocket session" }
            false
        }
    }
}