package com.mochame.sync.infrastructure.serialization.deprecated

import co.touchlab.kermit.Logger

abstract class VersionedCodec(
    protected val version: Byte,
    protected val logger: Logger
) {
    /**
     * Common helper to prepend the version header to serialized bits.
     */
    protected fun wrapHeader(payload: ByteArray): ByteArray {
        val envelope = ByteArray(1 + payload.size)
        envelope[0] = version
        payload.copyInto(envelope, destinationOffset = 1)
        return envelope
    }

    /**
     * Common helper to validate header version and strip the prefix.
     */
    protected fun unwrapHeader(bytes: ByteArray): ByteArray =
        bytes.copyOfRange(1, bytes.size)

    protected fun validate(bytes: ByteArray): Boolean =
        bytes.size > 1 && bytes[0] == version
}