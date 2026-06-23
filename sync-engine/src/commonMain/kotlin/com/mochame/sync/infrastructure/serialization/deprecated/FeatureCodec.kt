package com.mochame.sync.infrastructure.serialization.deprecated

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.platform.providers.BufferProvider
import com.mochame.sync.contract.models.LocalFirstEntity
import com.mochame.sync.contract.models.DecodeContext
import kotlinx.io.Source

abstract class FeatureCodec<T : LocalFirstEntity<T>>(
    protected val bufferProvider: BufferProvider,
    version: Byte,
    logger: Logger
) : VersionedCodec(version, logger) {

    fun encode(new: T, old: T?): ByteArray? {
        val deltaBytes = generateDelta(new, old) ?: return null
        return wrapHeader(deltaBytes)
    }

    /**
     * Children write directly into the provided sink.
     * Returns true if any bytes were written.
     */
    protected abstract fun generateDelta(new: T, old: T?): ByteArray?

    fun decode(data: ByteArray, context: DecodeContext): T {
        val payloadBits = unwrapHeader(data)
        return internalDecode(payloadBits, context)
    }

    /**
     * Submodules only implement the raw Protobuf-to-Object mapping.
     */
    protected abstract fun internalDecode(
        payloadBits: ByteArray,
        context: DecodeContext
    ): T

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

    abstract fun reconstructSummary(data: ByteArray): String

    abstract fun summarize(new: T, old: T?): String

}