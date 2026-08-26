package com.mochame.sync.common


import co.touchlab.kermit.Logger
import com.mochame.sync.api.exceptions.MochaException
import kotlinx.io.Source

/**
 * Conditions required for this to work:
 * * Every Protobuf message must begin with a Key.
 * * That key is an unsigned Varint.
 * * WireType 0 (Varint): Call readProtobufVarint() again on the value until MSB = 0.
 * * WireType 1 (Fixed 64-bit): Unconditionally skip 8 bytes.
 * * WireType 2 (Length-delimited): Read a Varint for length L, then skip L bytes.
 * * WireType 5 (Fixed 32-bit): Unconditionally skip 4 bytes.
 */
fun Source.readProtobufVarint(logger: Logger): Int {
    var value = 0
    var shift = 0
    var byteCount = 0

    try {
        while (true) {
            val byte = this.readByte().toInt()
            byteCount++

            val payload7Bit = byte and 0x7F
            value = value or (payload7Bit shl shift)
            val hasMoreBytes = (byte and 0x80) != 0

            logger.v {
                val hex = (byte and 0xFF).toString(16).padStart(2, '0').uppercase()
                val binary = (byte and 0xFF).toString(2).padStart(8, '0')
                "Varint byte #$byteCount [0x$hex | 0b$binary] -> " +
                        "7-bit payload=$payload7Bit (0b${payload7Bit.toString(2)}), " +
                        "shift=$shift, accumulative value=$value" +
                        if (hasMoreBytes) " [MSB=1: continuation byte follows]" else " [MSB=0: final byte]"
            }

            if (!hasMoreBytes) break

            shift += 7
            if (shift >= 32) throw Exception("Varint overflow (exceeded 32 bits)")
        }

        logger.v { "Varint total decoded value=$value across $byteCount byte(s)" }
        return value

    } catch (e: Exception) {
        logger.e(e) { "Binary Corruption: Failed to read Varint at shift $shift after $byteCount byte(s)" }
        throw MochaException.Persistent.CorruptionDetected("Varint overflow or unexpected EOF", e)
    }
}

fun Source.skipProtobufValue(wireType: Int, logger: Logger) {
    logger.v { "Skipping payload value for wireType=$wireType" }

    when (wireType) {
        0 -> { // Varint (int32, int64, bool, enum)
            val skippedVarint = this.readProtobufVarint(logger)
            logger.v { "Skipped WireType 0 (Varint) value=$skippedVarint" }
        }
        1 -> { // 64-bit fixed (double, fixed64)
            this.skip(8)
            logger.v { "Skipped WireType 1 (64-bit fixed: 8 bytes)" }
        }
        2 -> { // Length-delimited (string, bytes, sub-messages)
            val length = this.readProtobufVarint(logger).toLong()
            this.skip(length)
            logger.v { "Skipped WireType 2 (Length-Delimited: $length payload bytes)" }
        }
        5 -> { // 32-bit fixed (float, fixed32)
            this.skip(4)
            logger.v { "Skipped WireType 5 (32-bit fixed: 4 bytes)" }
        }
        else -> {
            val errorMsg = "Unsupported Wire Type: $wireType"
            logger.e { errorMsg }
            throw MochaException.Persistent.CorruptionDetected(errorMsg)
        }
    }
}