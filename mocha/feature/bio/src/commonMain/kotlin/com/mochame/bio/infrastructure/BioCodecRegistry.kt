package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.sync.domain.components.FeatureCodecRegistry
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.infrastructure.serialization.feature.FeatureCodec
import com.mochame.sync.infrastructure.serialization.feature.BaseFeatureCodecRegistry
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
) : BaseFeatureCodecRegistry<DailyContext, FeatureCodec<DailyContext>>(
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

    override fun summarize(new: DailyContext, old: DailyContext?): String {
        if (new.isDeleted) return "OP:DELETE"

        val tags = buildList {
            if (old == null || new.sleepHours != old.sleepHours) add(2)
            if (old == null || new.readinessScore != old.readinessScore) add(3)
            if (old == null || new.isNapped != old.isNapped) add(4)
        }

        return "OP:UPSERT_V1 ${
            tags.joinToString(prefix = "[", postfix = "]", separator = ",")
        }"
    }

}