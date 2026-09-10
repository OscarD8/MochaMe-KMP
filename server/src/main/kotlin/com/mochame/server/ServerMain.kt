package com.mochame.server

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.mochame.logger.CleanLogWriter
import com.mochame.sync.spi.network.SyncWireFrame
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        var sessionToEvict: DefaultWebSocketServerSession? = null

        mutex.withLock {
            val group = groups.getOrPut(groupId) { mutableMapOf() }
            val existingSession = group[nodeId]

            if (existingSession != null && existingSession != session) {
                logger.w {
                    "Attempt to register node '$nodeId' ($groupId). Matching session " +
                            "already exists. Replacing with new connection."
                }
                sessionToEvict = existingSession
            }

            group[nodeId] = session
            logger.i { "Node registered: '$nodeId' -> Group: '$groupId' (Total in group: ${group.size})" }
        }

        sessionToEvict?.let { old ->
            try {
                old.close(CloseReason(CloseReason.Codes.NORMAL, "Replaced by new connection"))
            } catch (_: Exception) {
            }
        }
    }

    suspend fun unregister(
        groupId: String,
        nodeId: String,
        session: DefaultWebSocketServerSession
    ) {
        mutex.withLock {
            val group = groups[groupId] ?: return@withLock
            if (group[nodeId] == session) {
                group.remove(nodeId)
                logger.i { "Node disconnected: '$nodeId' -> Group: '$groupId' (Remaining: ${group.size})" }
            }
            if (group.isEmpty()) {
                groups.remove(groupId)
            }
        }
    }

    suspend fun broadcast(groupId: String, senderNodeId: String, frameData: ByteArray) {
        val targets = mutex.withLock {
            groups[groupId]
                ?.filter { (nodeId, _) -> nodeId != senderNodeId }
                ?.toList() ?: emptyList()
        }

        if (targets.isEmpty()) return

        coroutineScope {
            for ((nodeId, session) in targets) {
                launch {
                    val sent = try {
                        withTimeoutOrNull(500.milliseconds) {
                            session.send(Frame.Binary(fin = true, data = frameData))
                            true
                        } ?: false
                    } catch (e: Exception) {
                        logger.w(e) { "Socket error sending to node '$nodeId' in group '$groupId'. Closing session." }
                        false
                    }

                    if (!sent) {
                        try {
                            session.close(
                                CloseReason(
                                    CloseReason.Codes.VIOLATED_POLICY,
                                    "Frame dropped. Cannot skip watermark."
                                )
                            )
                        } catch (_: Exception) {
                        }

                        unregister(groupId, nodeId, session)
                    }
                }
            }
        }
    }
}

fun main() {
    val database = ServerDatabase(dbPath)
    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val relayManager = RelayGroupManager()

    serverScope.launchLogCompactor(database, logger, retention = 40.days, interval = 1.hours)

    logger.i { "Starting Sync Relay Server on 0.0.0.0:8080..." }

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 30.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/sync/{groupId}/{nodeId}") {
                val groupId = call.parameters["groupId"] ?: return@webSocket close()
                val nodeId = call.parameters["nodeId"] ?: return@webSocket close()
                val sinceWatermark =
                    call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

                val minWatermark = database.getMinWatermark(groupId)
                if (sinceWatermark > 0 && minWatermark != null && sinceWatermark < minWatermark) {
                    logger.w { "Node '$nodeId' watermark $sinceWatermark is older than min retained $minWatermark" }
                    close(
                        CloseReason(
                            CloseReason.Codes.VIOLATED_POLICY,
                            "DELTA_HISTORY_EXPIRED"
                        )
                    )
                    return@webSocket
                }

                relayManager.register(groupId, nodeId, this)

                val catchupDeltas = database.getDeltasSince(
                    groupId = groupId,
                    excludeNodeId = nodeId,
                    sinceWatermark = sinceWatermark
                )

                try {
                    if (catchupDeltas.isNotEmpty()) {
                        logger.i { "Streaming ${catchupDeltas.size} backlogged deltas to '$nodeId'" }
                        for (delta in catchupDeltas) {
                            send(
                                Frame.Binary(
                                    fin = true,
                                    data = SyncWireFrame.delta(
                                        delta.watermark,
                                        delta.payload
                                    )
                                )
                            )
                        }
                    }

                    send(Frame.Binary(fin = true, data = SyncWireFrame.backfillComplete()))
                } catch (e: Exception) {
                    logger.w(e) { "Catch-up stream failed for node '$nodeId'. Aborting session to force clean retry." }
                    return@webSocket
                }

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Binary) {
                            val rawPayload = frame.readBytes()

                            val assignedWatermark = database.insertDelta(
                                groupId = groupId,
                                originNodeId = nodeId,
                                payload = rawPayload
                            ).also { logger.v { "Intent by $nodeId. Assigned watermark: $it" } }

                            send(Frame.Binary(true, SyncWireFrame.ack(assignedWatermark)))

                            val peerFrame = SyncWireFrame.delta(assignedWatermark, rawPayload)
                            relayManager.broadcast(
                                groupId = groupId,
                                senderNodeId = nodeId,
                                frameData = peerFrame
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    logger.i { "Session cancelled for node '$nodeId'" }
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Live loop error for node '$nodeId' in group '$groupId': ${e.message}" }
                } finally {
                    relayManager.unregister(groupId, nodeId, this)
                }
            }
        }
    }.start(wait = true)
}

fun CoroutineScope.launchLogCompactor(
    database: ServerDatabase,
    logger: Logger,
    retention: Duration = 30.days,
    interval: Duration = 1.hours,
): Job = launch {
    while (isActive) {
        try {
            val cutoff = System.currentTimeMillis() - retention.inWholeMilliseconds
            val pruned = database.pruneExpiredDeltas(cutoff)
            if (pruned > 0) {
                logger.i { "Pruned $pruned expired deltas from log." }
            }
        } catch (e: Exception) {
            logger.e(e) { "Log compaction task failed." }
        }
        delay(interval)
    }
}
