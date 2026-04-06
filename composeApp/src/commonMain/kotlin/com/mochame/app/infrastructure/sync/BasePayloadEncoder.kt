package com.mochame.app.infrastructure.sync

import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.LocalFirstEntity
import com.mochame.app.domain.sync.PayloadEncoder
import com.mochame.app.domain.sync.model.EntityMetadata
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

abstract class BasePayloadEncoder<T : LocalFirstEntity<T>>(
    protected val version: Byte
) : PayloadEncoder<T> {

    final override fun encode(new: T, old: T?): ByteArray? {
        val delta = generateDelta(new, old) ?: return null // no changes

        val buffer = Buffer()
        buffer.writeByte(version)
        buffer.write(delta)

        return buffer.readByteArray()
    }

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
     *
     */
    protected fun readVarint(buffer: Buffer): Int {
        var value = 0
        var shift = 0
        while (true) {
            val byte = buffer.readByte().toInt()
            value = value or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
            if (shift >= 32) throw MochaException.Persistent.CorruptionDetected("Varint overflow")
        }
        return value
    }

    /**
     * Skips payload content based on Wire Type without copying bytes.
     */
    protected fun skipValue(wireType: Int, buffer: Buffer) {
        when (wireType) {
            0 -> readVarint(buffer) // Skip Varint (Int32, Int64, Bool, Enum)
            1 -> buffer.skip(8)      // Skip 64-bit (Double, Fixed64)
            2 -> {                   // Skip Length-delimited (String, Bytes, Embedded Messages)
                val length = readVarint(buffer).toLong()
                buffer.skip(length)
            }
            5 -> buffer.skip(4)      // Skip 32-bit (Float, Fixed32)
            else -> throw MochaException.Persistent.CorruptionDetected("Unsupported Wire Type: $wireType")
        }
    }
}