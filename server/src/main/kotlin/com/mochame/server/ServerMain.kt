package com.mochame.server

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.mochame.logger.CleanLogWriter
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = Logger(
    config = StaticConfig(
        minSeverity = Severity.Verbose,
        logWriterList = listOf(CleanLogWriter(minSeverity = Severity.Verbose))
    ),
    tag = "RelayServer"
)

private class RelayGroupManager {
    private val mutex = Mutex()
    private val groups = mutableMapOf<String, MutableMap<String, DefaultWebSocketServerSession>>()
    suspend fun register(groupId: String, nodeId: String, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val group = groups.getOrPut(groupId) { mutableMapOf() }

            val existingSession = group[nodeId]
            if (existingSession != null && existingSession != session) {
                try {
                    existingSession.close(CloseReason(CloseReason.Codes.NORMAL, "Replaced by new connection"))
                } catch (_: Exception) { }
            }

            group[nodeId] = session
            logger.i { "Node registered: '$nodeId' -> Group: '$groupId' (Total in group: ${groups[groupId]?.size})" }
        }
    }

    suspend fun unregister(groupId: String, nodeId: String, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val group = groups[groupId] ?: return@withLock
            // Only remove if it's the exact same session instance
            if (group[nodeId] == session) {
                group.remove(nodeId)
                logger.i { "Node disconnected: '$nodeId' -> Group: '$groupId' (Remaining: ${group.size})" }
            }
            if (group.isEmpty()) {
                groups.remove(groupId)
            }
        }
    }

    suspend fun broadcast(groupId: String, senderNodeId: String, sender: DefaultWebSocketServerSession, data: ByteArray) {
        val targets = mutex.withLock {
            groups[groupId]
                ?.filter { (nodeId, _) -> nodeId != senderNodeId }
                ?.values
                ?.toList() ?: emptyList()
        }

        logger.i { "Broadcasting ${data.size} bytes from '$senderNodeId' to ${targets.size} peer(s) in group '$groupId'" }

        for (session in targets) {
            try {
                session.send(Frame.Binary(fin = true, data = data))
            } catch (e: Exception) {
                logger.w(e) { "Failed to deliver frame to peer in group '$groupId'" }
            }
        }
    }
}

fun main() {
    val relayManager = RelayGroupManager()

    logger.i { "Starting Sync Relay Server on 0.0.0.0:8080..." }

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        install(WebSockets)

        routing {
            webSocket("/sync/{groupId}/{nodeId}") {
                val groupId = call.parameters["groupId"] ?: return@webSocket close()
                val nodeId = call.parameters["nodeId"] ?: return@webSocket close()

                relayManager.register(groupId, nodeId, this)

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Binary) {
                            relayManager.broadcast(groupId, nodeId, sender = this, data = frame.readBytes())
                        }
                    }
                } finally {
                    relayManager.unregister(groupId, nodeId, this)
                }
            }
        }
    }.start(wait = true)
}