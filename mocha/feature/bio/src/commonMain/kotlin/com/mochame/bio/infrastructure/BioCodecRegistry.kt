package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.sync.domain.components.FeatureCodecRegistry
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.infrastructure.serialization.feature.FeatureCodec
import com.mochame.sync.infrastructure.serialization.feature.OverflowAwareCodecRegistry
import org.koin.core.annotation.Single

/**
 * Routes all binary operations based on the 1-byte header.
 * If adding a new version, ensure methods are updated to route that version.
 */
@Single(binds = [FeatureCodecRegistry::class])
class BioCodecRegistry(
    v1: BioCodecV1,
    // private val v2: BioPayloadEncoderV2 - demonstration
    logger: Logger
) : OverflowAwareCodecRegistry<DailyContext, FeatureCodec<DailyContext>>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger,
), FeatureCodecRegistry<DailyContext> {

    override fun encode(new: DailyContext, old: DailyContext?) =
        latestCodec.encode(new, old)

    override fun decode(data: ByteArray, context: DecodeContext) =
        routePayload(data) { codec, payload -> codec.decode(payload, context) }

    override fun reconstructSummary(data: ByteArray): String =
        routePayload(data) { codec, bytes -> codec.reconstructSummary(bytes) }

    /**
     * I don't know to route the right codec on a decode intent that needs summarizing.
     * I don't know if that is even needed. For now routing the most recent codec.
     */
    override fun summarize(new: DailyContext, old: DailyContext?): String =
        latestCodec.summarize(new, old)

}