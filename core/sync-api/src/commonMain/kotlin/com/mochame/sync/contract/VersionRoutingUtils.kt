package com.mochame.sync.contract

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import kotlinx.io.Source


interface VersionRouter<T : Any> {
    val latestVersion: Byte
    val versionMap: Map<Byte, T>
}

val <T : Any> VersionRouter<T>.latestCodec: T
    get() = versionMap[latestVersion] ?: throw MochaException.Persistent.CorruptionDetected(
        "No component registered for version $latestVersion"
    )


/**
 * Universal framing for outbound data.
 */
inline fun prependVersionTo(
    version: Byte,
    logger: Logger,
    block: () -> ByteArray?
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
 * Strips the version from the provided bytes, and passes back the stripped bytes
 * alongside the corresponding codec version from the provided map.
 *
 * In the intended use case, all routers paste this
 * into their versioned method call, and therefore check bytes exist,
 * strip the version, map the codec, and proceed with their own codec handling.
 *
 * Future me start here for time and space complexity.
 */
inline fun <T, R> stripAndVersionCodec(
    bytes: ByteArray,
    codecMap: Map<Byte, T>,
    logger: Logger,
    block: (T, ByteArray) -> R
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
inline fun <T, R> stripAndVersionCodec(
    source: Source,
    codecMap: Map<Byte, T>,
    logger: Logger,
    block: (T, Source) -> R
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

