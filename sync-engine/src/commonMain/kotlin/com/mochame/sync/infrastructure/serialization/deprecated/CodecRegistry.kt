package com.mochame.sync.infrastructure.serialization.deprecated

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException

abstract class CodecRegistry<TCodec : Any>(
    protected val codecMap: Map<Byte, TCodec>,
    protected val logger: Logger,
    latestVersion: Byte,
) {
    protected val latestCodec: TCodec = codecMap[latestVersion]
        ?: throw IllegalStateException("No codec registered for version $latestVersion")

    /**
     * Confirms basic payload validity, checks version header against its map to determine the concrete codec,
     * strips the version header and chains the return value of [block], to which it passes
     * the codec and the stripped payload.
     */
    protected inline fun <R> routePayload(
        bytes: ByteArray,
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

        return block(codec, bytes)
    }
}