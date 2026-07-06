package com.mochame.utils

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import kotlinx.io.Source



fun Source.readProtobufVarint(logger: Logger): Int {
    var value = 0
    var shift = 0
    try {
        while (true) {
            val byte = this.readByte().toInt()
            value = value or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
            if (shift >= 32) throw Exception("Varint overflow")
        }
        return value
    } catch (e: Exception) {
        logger.e(e) { "Binary Corruption: Failed to read Varint at shift $shift" }
        throw MochaException.Persistent.CorruptionDetected("Varint overflow")
    }
}

fun Source.skipProtobufValue(wireType: Int, logger: Logger) {
    when (wireType) {
        0 -> this.readProtobufVarint(logger)
        1 -> this.skip(8)
        2 -> {
            val length = this.readProtobufVarint(logger).toLong()
            this.skip(length)
        }
        5 -> this.skip(4)
        else -> throw MochaException.Persistent.CorruptionDetected("Unsupported Wire Type: $wireType")
    }
}
