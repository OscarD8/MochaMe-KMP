package com.mochame.sync.infrastructure.codec.intent

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.sync.domain.model.SyncIntent

abstract class BaseSyncIntentCodec(
    protected val version: Byte,
    protected val logger: Logger
) {
    /**
     * Appends own version to the encoded sync envelope.
     */
    internal fun encode(intent: SyncIntent): ByteArray {
        val bits = encodePayload(intent)
        val result = ByteArray(bits.size + 1)
        result[0] = version
        bits.copyInto(result, destinationOffset = 1)
        return result
    }

    /**
     * Strips version header and passes bytes that should represent a SyncIntent model.
     */
    internal fun decode(bytes: ByteArray): SyncIntent {
        if (!validate(bytes)) throw MochaException.Persistent.CorruptionDetected(
            "Batch size contained only a version, or failed a further protocol check."
        )
        val bits = bytes.copyOfRange(1, bytes.size)
        return decodePayload(bits)
    }

    protected abstract fun encodePayload(intent: SyncIntent): ByteArray
    protected abstract fun decodePayload(bits: ByteArray): SyncIntent
    protected fun validate(bytes: ByteArray): Boolean =
        bytes.size > 1 && bytes[0] == version
}