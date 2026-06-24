package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.latestCodec
import com.mochame.sync.contract.stripAndVersionCodec
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity
import com.mochame.sync.contract.prependVersionTo

abstract class FeatureCodecRouter<T : LocalFirstEntity<T>>(
    override val latestVersion: Byte,
    override val versionMap: Map<Byte, FeatureCodec<T>>,
    private val logger: Logger
) : VersionRouter<FeatureCodec<T>>, FeatureCodecRegistry<T> {

    override fun versionedEncode(new: T, old: T?): ByteArray? {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(new, old)
        }
    }

    override fun versionedDecode(data: ByteArray, context: DecodeContext): T {
        return stripAndVersionCodec(data, versionMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes, context)
        }
    }

    override fun versionedSummaryReconstruction(data: ByteArray): String {
        return stripAndVersionCodec(data, versionMap, logger) { codec, cleanBytes ->
            codec.reconstructSummary(cleanBytes)
        }
    }

    override fun versionedSummarize(new: T, old: T?): String {
        return latestCodec.summarize(new, old)
    }
}
