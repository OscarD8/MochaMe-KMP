package com.mochame.sync.spi.network

import com.mochame.sync.common.readLongAt
import com.mochame.sync.common.writeLongAt


interface SyncTransport {
    suspend fun connect(host: String, port: Int, groupId: String)

    suspend fun send(payload: ByteArray): Boolean

    fun pause()
    fun resume()

    fun registerInboundHandler(onReceived: suspend (Long, ByteArray) -> Unit)
    fun setOnConnectedListener(onConnected: suspend () -> Unit)
}

sealed interface InboundWireFrame {
    data object BackfillComplete : InboundWireFrame
    data class Ack(val watermark: Long) : InboundWireFrame
    data class Delta(val watermark: Long, val payload: ByteArray) : InboundWireFrame
}

object SyncWireFrame {
    private const val OP_BACKFILL_COMPLETE: Byte = 0x01
    private const val OP_ACK: Byte = 0x02
    private const val OP_DELTA: Byte = 0x03

    fun backfillComplete(): ByteArray = byteArrayOf(OP_BACKFILL_COMPLETE)

    fun ack(watermark: Long): ByteArray {
        val out = ByteArray(9)
        out[0] = OP_ACK
        out.writeLongAt(1, watermark)
        return out
    }

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
                require(bytes.size == 9) { "Malformed Ack frame: Expected 9 bytes, got ${bytes.size}" }
                val watermark = bytes.readLongAt(1)
                InboundWireFrame.Ack(watermark)
            }

            OP_DELTA -> {
                require(bytes.size >= 9) { "Malformed Delta frame: Expected >= 9 bytes, got ${bytes.size}" }
                val watermark = bytes.readLongAt(1)
                val payload = bytes.copyOfRange(9, bytes.size)
                InboundWireFrame.Delta(watermark, payload)
            }

            else -> error("Unknown wire frame opcode: ${bytes[0]}")
        }
    }
}