package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * Universal framing for outbound data.
 */
inline fun prependVersionTo(
    version: Byte,
    logger: Logger,
    crossinline block: () -> ByteArray?
): ByteArray {
    val payload = block() ?: run {
        logger.e { "Cannot prepend version. Provided execution block returned a null ByteArray." }
        throw MochaException.Persistent.CorruptionDetected("Cannot prepend version. Execution returned null ByteArray.")
    }

    logger.d { "Encoding processed | Size: ${payload.size} bytes | Version Header: $version" }

    val envelope = ByteArray(1 + payload.size)
    envelope[0] = version
    payload.copyInto(envelope, destinationOffset = 1)
    return envelope
}

/**
 * Universal de-multiplexing for inbound data (from ByteArray).
 */
inline fun <TCodec, R> contextualByteDecoding(
    bytes: ByteArray,
    codecMap: Map<Byte, TCodec>,
    logger: Logger,
    crossinline block: (TCodec, ByteArray) -> R
): R {
    if (bytes.isEmpty()) {
        throw MochaException.Persistent.CorruptionDetected("Empty payload envelope received.")
    }

    val version = bytes[0]
    val codec = codecMap[version] ?: run {
        logger.e { "Unable to fetch codec. Unknown protocol version byte: $version" }
        throw MochaException.Persistent.UnknownProtocolVersion(version)
    }

    val strippedPayload = bytes.copyOfRange(1, bytes.size)

    return block(codec, strippedPayload)
}

/**
 * Universal de-multiplexing for inbound data (directly from Source).
 */
inline fun <TCodec, R> contextualSourceDecoding(
    source: Source,
    codecMap: Map<Byte, TCodec>,
    logger: Logger,
    crossinline block: (TCodec, Source) -> R
): R {
    if (source.exhausted()) {
        throw MochaException.Persistent.CorruptionDetected("Empty payload envelope received.")
    }

    val version = source.readByte()
    val codec = codecMap[version] ?: run {
        logger.e { "Unable to fetch codec. Unknown protocol version byte: $version" }
        throw MochaException.Persistent.UnknownProtocolVersion(version)
    }

    return block(codec, source)
}
