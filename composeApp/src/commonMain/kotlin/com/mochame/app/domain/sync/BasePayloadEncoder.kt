package com.mochame.app.domain.sync

import com.mochame.app.domain.exceptions.MochaException
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
        // 1. SSL Guard: Ensure we aren't decoding the wrong language
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
}