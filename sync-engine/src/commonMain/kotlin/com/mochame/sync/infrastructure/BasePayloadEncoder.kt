package com.mochame.sync.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.model.LocalFirstEntity
import com.mochame.sync.domain.PayloadEncoder
import com.mochame.sync.domain.model.EntityMetadata
import com.mochame.sync.domain.providers.BufferProvider
import com.mochame.utils.exceptions.MochaException
import kotlinx.io.Source

abstract class BasePayloadEncoder<T : LocalFirstEntity<T>>(
    protected val version: Byte,
    protected val bufferProvider: BufferProvider,
    protected val logger: Logger
) : PayloadEncoder<T> {

    final override fun encode(new: T, old: T?): ByteArray? {
        val bits = generateDelta(new, old) ?: return null

        // Simple 2-copy process (Header + Bits)
        val result = ByteArray(bits.size + 1)
        result[0] = version
        bits.copyInto(result, destinationOffset = 1)
        return result
    }

    /**
     * Children write directly into the provided sink.
     * Returns true if any bytes were written.
     */
    abstract fun generateDelta(new: T, old: T?): ByteArray?

    /**
     * Validates and strips the header.
     */
    final override fun decode(data: ByteArray, metadata: EntityMetadata): T {
        // 1. Ensure we aren't decoding the wrong language
        if (!validate(data)) {
            throw MochaException.Persistent.UnknownProtocolVersion(
                data.getOrNull(0) ?: -1
            )
        }

        // 2. Header Stripping: Remove the first byte
        val payloadBits = data.copyOfRange(1, data.size)

        // 3. Delegation: Pass the raw bits to the submodule
        return internalDecode(payloadBits, metadata)
    }

    /**
     * Submodules only implement the raw Protobuf-to-Object mapping.
     */
    abstract fun internalDecode(payloadBits: ByteArray, metadata: EntityMetadata): T

    /**
     * Performs a non-allocating scan of the Protobuf bitstream.
     */
    protected fun readVarint(source: Source): Int {
        var value = 0
        var shift = 0
        try {
            while (true) {
                val byte = source.readByte().toInt()
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

    /**
     * Skips payload content based on Wire Type without copying bytes.
     */
    protected fun skipValue(wireType: Int, source: Source) {
        when (wireType) {
            0 -> readVarint(source) // Skip Varint (Int32, Int64, Bool, Enum)
            1 -> source.skip(8)      // Skip 64-bit (Double, Fixed64)
            2 -> {                   // Skip Length-delimited (String, Bytes, Embedded Messages)
                val length = readVarint(source).toLong()
                source.skip(length)
            }
            5 -> source.skip(4)      // Skip 32-bit (Float, Fixed32)
            else -> throw MochaException.Persistent.CorruptionDetected("Unsupported Wire Type: $wireType")
        }
    }
}