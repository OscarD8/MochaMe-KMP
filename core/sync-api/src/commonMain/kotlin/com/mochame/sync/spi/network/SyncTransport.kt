package com.mochame.sync.spi.network

import com.mochame.sync.common.readLongAt
import com.mochame.sync.common.writeLongAt


interface SyncTransport {
    val isConnected: Boolean
    suspend fun connect(host: String, port: Int, groupId: String)

    suspend fun send(batchId: Long, payload: ByteArray): SendResult

    fun pause()
    fun resume()

    fun registerInboundAckHandler(onAck: suspend (Long, Long) -> Unit)
    fun registerInboundDeltaHandler(onReceived: suspend (Long, ByteArray) -> Unit)
    fun setOnConnectedListener(onConnected: suspend () -> Unit)
    fun setOnDisconnectedListener(onDisconnected: suspend () -> Unit)

}

sealed interface SendResult {
    data object Success : SendResult
    data object NoConnection : SendResult
    data class Failure(val cause: Throwable) : SendResult
}

sealed interface InboundWireFrame {
    data object BackfillComplete : InboundWireFrame
    data class Ack(val batchId: Long, val watermark: Long) : InboundWireFrame
    data class Delta(val watermark: Long, val payload: ByteArray) : InboundWireFrame
    data class ClientSubmit(val batchId: Long, val payload: ByteArray) : InboundWireFrame
}

object SyncWireFrame {
    private const val OP_BACKFILL_COMPLETE: Byte = 0x01
    private const val OP_ACK: Byte = 0x02
    private const val OP_DELTA: Byte = 0x03
    private const val OP_CLIENT_SUBMIT: Byte = 0x04

    fun backfillComplete(): ByteArray = byteArrayOf(OP_BACKFILL_COMPLETE)

    fun ack(batchId: Long, watermark: Long): ByteArray {
        val out = ByteArray(17)
        out[0] = OP_ACK
        out.writeLongAt(1, batchId)
        out.writeLongAt(9, watermark)
        return out
    }

    // Client -> Server (Upstream Push)
    fun batch(batchId: Long, payload: ByteArray): ByteArray {
        val out = ByteArray(9 + payload.size)
        out[0] = OP_CLIENT_SUBMIT
        out.writeLongAt(1, batchId)
        payload.copyInto(out, destinationOffset = 9)
        return out
    }

    // Server -> Client (Downstream Broadcast)
    fun delta(watermark: Long, payload: ByteArray): ByteArray {
        val out = ByteArray(9 + payload.size)
        out[0] = OP_DELTA
        out.writeLongAt(1, watermark)
        payload.copyInto(out, destinationOffset = 9)
        return out
    }

    fun unwrap(bytes: ByteArray): InboundWireFrame {
        require(bytes.isNotEmpty()) { "Malformed frame: Empty payload" }

        return when (bytes[0]) {
            OP_BACKFILL_COMPLETE -> InboundWireFrame.BackfillComplete

            OP_ACK -> {
                require(bytes.size == 17) { "Malformed Ack frame: Expected 17 bytes, got ${bytes.size}" }
                val batchId = bytes.readLongAt(1)
                val watermark = bytes.readLongAt(9)
                InboundWireFrame.Ack(batchId, watermark)
            }

            OP_DELTA -> {
                require(bytes.size >= 9) { "Malformed Delta frame: Expected >= 9 bytes, got ${bytes.size}" }
                val watermark = bytes.readLongAt(1)
                val payload = bytes.copyOfRange(9, bytes.size)
                InboundWireFrame.Delta(watermark, payload)
            }

            OP_CLIENT_SUBMIT -> {
                require(bytes.size >= 9) { "Malformed ClientSubmit frame: Expected >= 9 bytes, got ${bytes.size}" }
                val batchId = bytes.readLongAt(1)
                val payload = bytes.copyOfRange(9, bytes.size)
                InboundWireFrame.ClientSubmit(batchId, payload)
            }

            else -> error("Unknown wire frame opcode: 0x${bytes[0].toString(16)}")
        }
    }
}