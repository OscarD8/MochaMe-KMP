package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.spi.network.SyncTransport
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

@Single(binds = [SyncTransport::class])
internal class KtorWebSocketSyncTransport(
    @AppBackgroundScope private val backgroundScope: CoroutineScope,
    logger: Logger
) : SyncTransport {

    private val logger =
        logger.withTags(LogTags.Layer.TRANSPORT, LogTags.Domain.SYNC, "MrKtor")

    private val client = HttpClient {
        install(WebSockets)
    }

    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null

    @Volatile
    private var inboundHandler: (suspend (ByteArray) -> Unit)? = null

    @Volatile
    private var onConnectedListener: (suspend () -> Unit)? = null

    override fun setOnConnectedListener(onConnected: suspend () -> Unit) {
        this.onConnectedListener = onConnected
    }

    override fun registerInboundHandler(onReceived: suspend (ByteArray) -> Unit) {
        this.inboundHandler = onReceived
    }

    override suspend fun connect(host: String, port: Int, groupId: String, nodeId: String) {
        backgroundScope.launch {
            while (isActive) {
                try {
                    client.webSocket(host = host, port = port, path = "/sync/$groupId/$nodeId") {
                        activeSession = this
                        logger.i { "WebSocket connected to $host:$port (Group: $groupId, Node: $nodeId)" }

                        onConnectedListener?.invoke()

                        for (frame in incoming) {
                            if (frame is Frame.Binary) {
                                inboundHandler?.invoke(frame.readBytes())
                            }
                        }
                    }
                } catch (e: Exception) {
                    activeSession = null
                    logger.w(e) { "WebSocket disconnected from relay: [${e::class.simpleName}] ${e.message}. Retrying in 3000ms..." }
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