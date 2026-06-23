package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity

open class FeatureRoutingRegistry<T : LocalFirstEntity<T>>(
    codecMap: Map<Byte, FeatureCodec<T>>,
    latestVersion: Byte,
    logger: Logger
) : VersionRoutingCodec<FeatureCodec<T>>(codecMap, latestVersion, logger),
    FeatureCodecRegistry<T> {

    override fun encode(new: T, old: T?): ByteArray? {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(new, old)
        }
    }

    override fun decode(data: ByteArray, context: DecodeContext): T {
        return contextualByteDecoding(data, codecMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes, context)
        }
    }

    override fun reconstructSummary(data: ByteArray): String {
        return contextualByteDecoding(data, codecMap, logger) { codec, cleanBytes ->
            codec.reconstructSummary(cleanBytes)
        }
    }

    override fun summarize(new: T, old: T?): String {
        return latestCodec.summarize(new, old)
    }
}
