package com.mochame.server.relay

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

sealed interface BackfillResult {
    data object Success : BackfillResult
    data object ChannelClosed : BackfillResult
    data class Failure(val cause: Exception) : BackfillResult
}

class SessionHandle(
    val nodeId: String,
    val groupId: String,
    val session: WebSocketSession
) {
    val outboundChannel: Channel<Frame> = Channel(capacity = 256)

    private val stagingBuffer = ArrayDeque<Pair<Long, Frame>>()
    private val stagingLock = Any()
    private val maxStagingCapacity = 512

    @Volatile
    var isBackfilled: Boolean = false
        private set

    /**
     * Non-blocking broadcast entry called by DatabaseActor.
     * Returns false ONLY if staging capacity or direct egress capacity is saturated.
     */
    fun enqueueBroadcast(watermark: Long, frame: Frame): Boolean {
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
     * Processes staging sequentially. Suspends cooperatively on outboundChannel.send()
     * outside the lock, providing exact error causality if interrupted.
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
}