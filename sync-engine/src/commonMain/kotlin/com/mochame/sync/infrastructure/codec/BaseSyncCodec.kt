package com.mochame.sync.infrastructure.codec

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.sync.domain.model.SyncIntent

abstract class BaseSyncCodec(
    protected val version: Byte,
    protected val logger: Logger
) {

    /**
     * Appends own version to the encoded sync envelope.
     */
    fun encode(intent: SyncIntent): ByteArray {
        val bits = encodePayload(intent)
        val result = ByteArray(bits.size + 1)
        result[0] = version
        bits.copyInto(result, destinationOffset = 1)
        return result
    }

    /**
     * Strips version header and passes bytes that should represent a SyncIntent model.
     */
    fun decode(bytes: ByteArray): SyncIntent {
        if (!validate(bytes)) throw MochaException.Persistent.UnknownProtocolVersion(
            bytes.getOrNull(0) ?: -1
        )
        val bits = bytes.copyOfRange(1, bytes.size)
        return decodePayload(bits)
    }

    abstract fun encodePayload(intent: SyncIntent): ByteArray
    abstract fun decodePayload(bits: ByteArray): SyncIntent
    abstract fun validate(bytes: ByteArray): Boolean
}