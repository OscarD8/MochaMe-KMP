package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException

class WireEnvelopeMux() {

    fun prependHeader(version: Byte, payload: ByteArray): ByteArray {
        val envelope = ByteArray(1 + payload.size)
        envelope[0] = version
        payload.copyInto(envelope, destinationOffset = 1)
        return envelope
    }

    inline fun <TCodec, R> route(
        bytes: ByteArray,
        codecMap: Map<Byte, TCodec>,
        logger: Logger,
        block: (TCodec, ByteArray) -> R
    ): R {
        if (bytes.isEmpty()) {
            throw MochaException.Persistent.CorruptionDetected("Empty payload received.")
        }
        val version = bytes[0]
        val codec = codecMap[version] ?: run {
            logger.e { "Unknown protocol version: $version" }
            throw MochaException.Persistent.UnknownProtocolVersion(version)
        }
        // Strips the byte header inline and passes it forward
        val strippedPayload = bytes.copyOfRange(1, bytes.size)
        return block(codec, strippedPayload)
    }
}