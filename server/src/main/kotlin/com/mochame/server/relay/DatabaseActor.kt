package com.mochame.server.relay

import co.touchlab.kermit.Logger
import com.mochame.server.config.ServerConfig
import com.mochame.server.config.ServerLogger
import com.mochame.server.database.ServerDatabase
import com.mochame.sync.spi.network.SyncWireFrame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.INTERNAL_ERROR
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class DeltaWriteIntent(
    val groupId: String,
    val originNodeId: String,
    val batchId: Long,
    val rawPayload: ByteArray,
    val senderHandle: SessionHandle
)

class DatabaseActor(
    private val database: ServerDatabase,
    private val relayManager: RelayManager,
    private val logger: Logger = ServerLogger.base.withTag("Db_Actor"),
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBatchSize: Int = ServerConfig.MAX_BATCH_SIZE,
) {
    val writeChannel = Channel<DeltaWriteIntent>(capacity = 1000)

    init {
        scope.launch(dispatcher.limitedParallelism(1)) {
            val batch = ArrayList<DeltaWriteIntent>(maxBatchSize)

            for (firstIntent in writeChannel) {
                batch.add(firstIntent)

                while (batch.size < maxBatchSize) {
                    val next = writeChannel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }

                try {
                    val watermarks = database.insertBatch(batch)

                    for (i in batch.indices) {
                        val intent = batch[i]
                        val watermark = watermarks[i]

                        val ackFrame = Frame.Binary(
                            fin = true,
                            data = SyncWireFrame.ack(intent.batchId, watermark)
                        )
                        intent.senderHandle.outboundChannel.trySend(ackFrame)
                            .onClosed { cause ->
                                relayManager.terminateSession(
                                    intent.senderHandle,
                                    INTERNAL_ERROR,
                                    "ACK_SEND_FAILURE: Channel closed/cancelled (${cause?.message})"
                                )
                            }
                            .onFailure { cause ->
                                if (cause == null) {
                                    relayManager.terminateSession(
                                        intent.senderHandle,
                                        INTERNAL_ERROR,
                                        "ACK_SEND_FAILURE: Outbound buffer saturated"
                                    )
                                }
                            }

                        val broadcastFrame = Frame.Binary(
                            fin = true,
                            data = SyncWireFrame.delta(watermark, intent.rawPayload)
                        )

                        relayManager.broadcast(
                            groupId = intent.groupId,
                            excludeNodeId = intent.originNodeId,
                            watermark = watermark,
                            frame = broadcastFrame
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val uniqueSenders = batch.map { it.senderHandle }.distinctBy { it.nodeId }
                    handleBatchFailure(uniqueSenders, e)
                } finally {
                    batch.clear()
                }
            }
        }
    }

    private fun handleBatchFailure(uniqueSenders: List<SessionHandle>, error: Exception) {
        logger.e(error) {
            """
            |Batch write failed (${error.message}) for the following senders. Terminating connections:
            |${uniqueSenders.joinToString(separator = "\n") { "|  - ${it.nodeId}" }}
            """.trimMargin()
        }

        val failureReason = "DATABASE_BATCH_WRITE_ERROR: ${error.message ?: "Commit failure"}"

        for (sender in uniqueSenders) {
            relayManager.terminateSession(
                peer = sender,
                code = INTERNAL_ERROR,
                reason = failureReason
            )
        }
    }
}