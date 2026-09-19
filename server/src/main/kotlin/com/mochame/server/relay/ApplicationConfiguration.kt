package com.mochame.server.relay

import co.touchlab.kermit.Logger
import com.mochame.server.config.ServerConfig
import com.mochame.server.database.ServerDatabase
import com.mochame.sync.common.readLongAt
import com.mochame.sync.spi.network.SyncWireFrame
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.INTERNAL_ERROR
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun Application.configureServer(
    database: ServerDatabase,
    relayManager: RelayManager,
    databaseActor: DatabaseActor,
    logger: Logger
) {
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
            val sinceWatermark = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L

            // 1. Hard Retention Boundary Check
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

            // 2. Soft Backfill Volume Boundary Check (Snapshot Redirection)
            val pendingDeltaCount = database.countDeltasSince(groupId, nodeId, sinceWatermark)
            if (pendingDeltaCount > ServerConfig.MAX_BACKFILL_THRESHOLD) {
                logger.i { "Node '$nodeId' backlog ($pendingDeltaCount deltas) exceeds threshold. Enforcing snapshot sync." }
                close(
                    CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY,
                        "SNAPSHOT_REQUIRED: Backlog exceeds ${ServerConfig.MAX_BACKFILL_THRESHOLD} deltas"
                    )
                )
                return@webSocket
            }

            // 3. Register Session and Launch Single-Egress Outbound Pump
            val sessionHandle = SessionHandle(nodeId, groupId, this)
            val evicted = relayManager.register(sessionHandle)
            evicted?.session?.launch {
                try {
                    evicted.session.close(
                        CloseReason(CloseReason.Codes.NORMAL, "Replaced by new connection")
                    )
                } catch (_: Throwable) {
                }
            }

            val outboundJob = launch {
                try {
                    for (frame in sessionHandle.outboundChannel) {
                        send(frame)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Egress pump failed unexpectedly for node '$nodeId'" }
                } finally {
                    try {
                        close(
                            CloseReason(
                                CloseReason.Codes.NORMAL,
                                "Outbound collection completed"
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            // 4. Unified Session Lifecycle
            try {
                var currentSinceWatermark = sinceWatermark
                var maxBackfillWatermark = sinceWatermark
                var totalBackfilledCount = 0

                // Paged Backfill Stream
                while (true) {
                    val chunk = database.getDeltasSince(
                        groupId = groupId,
                        excludeNodeId = nodeId,
                        sinceWatermark = currentSinceWatermark,
                        limit = ServerConfig.MAX_BACKFILL_CHUNK_SIZE
                    )

                    if (chunk.isEmpty()) break

                    for (delta in chunk) {
                        sessionHandle.outboundChannel.send(
                            Frame.Binary(
                                fin = true,
                                data = SyncWireFrame.delta(delta.watermark, delta.payload)
                            )
                        )
                        if (delta.watermark > maxBackfillWatermark) {
                            maxBackfillWatermark = delta.watermark
                        }
                    }

                    currentSinceWatermark = maxBackfillWatermark
                    totalBackfilledCount += chunk.size

                    if (chunk.size < ServerConfig.MAX_BACKFILL_CHUNK_SIZE) break
                    yield()
                }

                if (totalBackfilledCount > 0) {
                    logger.i { "Paging catch-up complete for node '$nodeId': streamed $totalBackfilledCount deltas" }
                }

                // Delimit catch-up stream before live transition
                sessionHandle.outboundChannel.send(
                    Frame.Binary(fin = true, data = SyncWireFrame.backfillComplete())
                )

                when (val result = sessionHandle.completeBackfill(maxBackfillWatermark)) {
                    is BackfillResult.Success -> {
                        logger.v { "Node '$nodeId' backfill complete" }
                    }

                    is BackfillResult.ChannelClosed -> {
                        logger.i { "Node '$nodeId' disconnected during backfill completion." }
                        return@webSocket
                    }

                    is BackfillResult.Failure -> {
                        logger.e(result.cause) { "Backfill draining error for node '$nodeId'" }
                        relayManager.terminateSession(
                            peer = sessionHandle,
                            code = INTERNAL_ERROR,
                            reason = "BACKFILL_DRAIN_FAILURE: ${result.cause.message}"
                        )
                        return@webSocket
                    }
                }

                // Inbound Ingestion Loop
                for (frame in incoming) {
                    if (frame is Frame.Binary) {
                        val rawPayload = frame.readBytes()
                        if (rawPayload.size < 9) {
                            logger.w { "Malformed frame from '$nodeId' (${rawPayload.size}b). Discarding." }
                            continue
                        }

                        val batchId = rawPayload.readLongAt(1)

                        val intent = DeltaWriteIntent(
                            groupId = groupId,
                            originNodeId = nodeId,
                            batchId = batchId,
                            rawPayload = rawPayload,
                            senderHandle = sessionHandle
                        )

                        databaseActor.writeChannel.send(intent)
                    }
                }
            } catch (e: CancellationException) {
                logger.i { "Session cancelled for node '$nodeId'" }
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Session error for node '$nodeId' in group '$groupId': ${e.message}" }
            } finally {
                relayManager.unregister(groupId, nodeId, sessionHandle)
                sessionHandle.outboundChannel.close()
                outboundJob.cancel()
            }
        }
    }
}