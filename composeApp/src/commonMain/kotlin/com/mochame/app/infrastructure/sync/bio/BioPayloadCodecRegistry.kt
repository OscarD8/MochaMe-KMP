package com.mochame.app.infrastructure.sync.bio

import co.touchlab.kermit.Logger
import com.mochame.app.domain.feature.bio.DailyContext
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.sync.PayloadEncoder
import com.mochame.app.domain.sync.model.EntityMetadata
import com.mochame.app.infrastructure.logging.appendTag
import kotlinx.io.Buffer

/**
 * The App Release Version Dispatcher.
 * Routes all binary operations based on the 1-byte header.
 */
class BioPayloadCodecRegistry(
    private val v1: BioPayloadEncoderV1,
    // private val v2: BioPayloadEncoderV2 // FUTURE: 2027 Protocol Shift
    logger: Logger
) : PayloadEncoder<DailyContext> {

    private val logger = logger.appendTag("BioCodec")

    override fun encode(new: DailyContext, old: DailyContext?): Buffer? {
        //Always encode using the LATEST protocol available.
        return v1.generateDelta(new, old)
    }

    override fun validate(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        return when (data[0]) {
            0x01.toByte() -> true // V1 is active
            // 0x02.toByte() -> v2.validate(data) // FUTURE
            else -> false
        }
    }

    /**
     * Decoding Routing: Peeks at the header and picks the right engine.
     */
    override fun decode(data: ByteArray, metadata: EntityMetadata): DailyContext {
        if (data.isEmpty()) {
            logger.e { "Codec Registry: Received empty payload for ${metadata.id}" }
            throw MochaException.Persistent.CorruptionDetected("Empty Payload")
        }
        return when (data[0]) {
            0x01.toByte() -> v1.decode(data, metadata)
            // 0x02.toByte() -> v2.decode(data) // Route to the 2027 engine
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
        if (data.isEmpty()) return "OP:INVALID"
        return when (data[0]) {
            0x01.toByte() -> v1.reconstructSummary(data)
            // 0x02.toByte() -> v2.summarize(data) // FUTURE
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