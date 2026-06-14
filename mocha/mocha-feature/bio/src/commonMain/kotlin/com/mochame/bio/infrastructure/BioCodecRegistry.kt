package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.contract.exceptions.MochaException
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.components.FeatureCodecRegistry
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.infrastructure.codec.BaseFeatureCodecRegistry
import org.koin.core.annotation.Single

/**
 * The App Release Version identifier/router for binary payloads.
 * Routes all binary operations based on the 1-byte header.
 * If adding a new version, ensure [encode] is updated to route that version.
 */
@Single(binds = [FeatureCodecRegistry::class])
class BioCodecRegistry(
    private val v1: BioCodecV1,
    // private val v2: BioPayloadEncoderV2 - demonstration
    logger: Logger
) : BaseFeatureCodecRegistry<DailyContext>(
    logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.BIO,
        className = "BioCodecRegistry"
    )
) {

    override fun encode(new: DailyContext, old: DailyContext?): ByteArray? {
        return v1.encode(new, old) // Always encode using the latest protocol available
    }

    override fun validate(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        return when (data[0]) {
            0x01.toByte() -> v1.validate(data)
            // 0x02.toByte() -> v2.validate(data)
            else -> false
        }
    }

    /**
     * Decoding Routing: Peeks at the header and picks the right engine.
     */
    override fun decode(data: ByteArray, metadata: DecodeContext): DailyContext {
        if (data.isEmpty()) {
            logger.e { "Received empty payload for ${metadata.id}" }
            throw MochaException.Persistent.CorruptionDetected("Empty Payload")
        }
        return when (data[0]) {
            0x01.toByte() -> v1.decode(data, metadata)
            // 0x02.toByte() -> v2.decode(data)
            else -> {
                logger.e {
                    "Protocol Collision | ID: ${metadata.id} | " +
                            "Node received version ${data[0]} but only supports V1."
                }
                throw MochaException.Persistent.UnknownProtocolVersion(data[0])
            }
        }
    }

    /**
     * Forensic Overload: Summarize from Raw Bits.
     */
    override fun reconstructSummary(data: ByteArray): String {
        if (data.isEmpty()) return "Summary reconstruction attempt made against empty ByteArray."
        return when (data[0]) {
            0x01.toByte() -> v1.reconstructSummary(data)
            // 0x02.toByte() -> v2.summarize(data)
            else -> "OP:UNKNOWN_VERSION_${data[0]}"
        }
    }

    /**
     * Mutation Overload: Summarize from Live Objects.
     */
    override fun summarize(new: DailyContext, old: DailyContext?): String {
        // Always uses the latest summarizer (V1)
        return v1.summarize(new, old)
    }
}