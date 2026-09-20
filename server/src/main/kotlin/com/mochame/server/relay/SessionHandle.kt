package com.mochame.server.relay

import co.touchlab.kermit.Logger
import com.mochame.server.config.ServerConfig
import com.mochame.server.config.ServerLogger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result outcomes for completing the delta backfill phase.
 */
sealed interface BackfillResult {
    data object Success : BackfillResult
    data object ChannelClosed : BackfillResult
    data class Failure(val cause: Exception) : BackfillResult
}

/**
 * Encapsulates an active peer's WebSocket session, outbound pipeline, and staging buffer state.
 *
 * Coordinates the handoff between backfilling on connection (historical deltas), and broadcasted intents
 * from other peers in the group. Any backfilled deltas and concurrent deltas dispatched during
 * the backfilling process must end up in the outbound pipeline in sequence (by watermark).
 * If a single out of order watermark is shipped to the client, it's then possible for that node
 * to permanently drop that previous state.
 *
 * ###### Concurrency
 * - Calls via [enqueueBroadcast] are non-blocking and bypass locks once backfilled.
 * - Suspension on [outboundChannel] occurs outside locks to prevent stalling
 *   the database writer thread.
 * - Locking ensures the transition from processing a backfill to broadcasting received watermarks
 *   from other peers is atomic.
 */
class SessionHandle(
    val nodeId: String,
    val groupId: String,
    val session: WebSocketSession,
    config: ServerConfig = ServerConfig.Default,
    private val logger: Logger = ServerLogger.base.withTag(nodeId.take(8))
) {
    /**
     * Outbound channel for sending frames to the websocket. All outbound frames related to a session
     * must be sent through this channel.
     */
    val outboundChannel: SendChannel<Frame>
        field = Channel<Frame>(capacity = config.outboundChannelCapacity)

    private val stagingBuffer = ArrayDeque<Pair<Long, Frame>>()
    private val stagingLock = Any()
    private val maxStagingCapacity = config.outboundStagingCapacity

    @Volatile
    var isBackfilled: Boolean = false
        private set

    init {
        session.launch {
            runOutboundWorker()
        }
    }

    /**
     * Drains the internal outbound channel to the underlying WebSocket session until closed or canceled.
     */
    private suspend fun runOutboundWorker() {
        try {
            for (frame in outboundChannel) {
                session.send(frame)
            }
        } catch (e: CancellationException) {
            logger.d { "Outbound worker cancelled for node '$nodeId'" }
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Outbound worker failed unexpectedly for node '$nodeId'" }
            close(CloseReason.Codes.INTERNAL_ERROR, "Worker failure: ${e.message}")
        } finally {
            outboundChannel.cancel(CancellationException("Outbound worker finished"))
        }
    }

    /**
     * Broadcast a frame to the outbound channel.
     *
     * Bypasses locks once backfilled, sending directly to [outboundChannel].
     * Otherwise, stages the frame under [stagingLock] to ensure atomic transition from backfill
     * (processing into [stagingBuffer] to [outboundChannel]) to live broadcasts.
     *
     * @return `true` if accepted; `false` if staging or egress capacity is saturated (slow consumer).
     */
    fun enqueueBroadcast(watermark: Long, frame: Frame): Boolean {
        if (isBackfilled) {
            return outboundChannel.trySend(frame).isSuccess
        }

        synchronized(stagingLock) {
            return if (!isBackfilled) {
                if (stagingBuffer.size >= maxStagingCapacity) {
                    false // True slow consumer: staging limit exceeded during backfill
                } else {
                    stagingBuffer.addLast(watermark to frame)
                    true
                }
            } else {
                outboundChannel.trySend(frame).isSuccess
            }
        }
    }

    /**
     * Processes staging sequentially. Suspends on outboundChannel.send()
     * outside the lock.
     */
    suspend fun completeBackfill(maxWatermark: Long): BackfillResult {
        while (true) {
            val nextFrame: Frame = synchronized(stagingLock) {
                while (stagingBuffer.isNotEmpty() && stagingBuffer.first().first <= maxWatermark) {
                    stagingBuffer.removeFirst()
                }

                if (stagingBuffer.isEmpty()) {
                    isBackfilled = true
                    return BackfillResult.Success
                }

                stagingBuffer.removeFirst().second
            }

            try {
                outboundChannel.send(nextFrame)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ClosedSendChannelException) {
                return BackfillResult.ChannelClosed
            } catch (e: Exception) {
                return BackfillResult.Failure(e)
            }
        }
    }

    /**
     * Initiates connection closure. Transmits the provided code and reason over the wire,
     * cancelling the session scope and outbound worker.
     */
    fun close(
        code: CloseReason.Codes = CloseReason.Codes.NORMAL,
        reason: String = "Session closed"
    ) {
        logger.i { "Closing session for node '$nodeId' [${code.name}]: $reason" }

        outboundChannel.cancel(CancellationException(reason))

        session.launch {
            withContext(NonCancellable) {
                try {
                    session.close(CloseReason(code, reason))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(e) { "Failed sending close frame to node '$nodeId'" }
                }
            }
        }
    }
}

