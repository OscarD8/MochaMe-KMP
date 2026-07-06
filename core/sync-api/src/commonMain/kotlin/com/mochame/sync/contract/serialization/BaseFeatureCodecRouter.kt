package com.mochame.sync.contract.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.getCodec
import com.mochame.sync.contract.latestCodec
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity
import com.mochame.sync.contract.stripAndVersion

abstract class BaseFeatureCodecRouter<T : LocalFirstEntity<T>>(
    override val latestVersion: Int,
    override val versionRegistry: Array<FeatureCodec<T>?>,
    private val logger: Logger
) : FeatureCodecRouter<T, FeatureCodec<T>> {

    override fun routedEncode(new: T, old: T?): ByteArray? {
        return latestCodec.encode(new, old)
    }

    override fun routedDecode(data: ByteArray, context: DecodeContext): T {
        return getCodec(context.featureSchemaVersion).decode(data, context)
    }

    override fun versionedSummaryReconstruction(data: ByteArray): String {
        return stripAndVersion(data, data[0], logger) { codec, cleanBytes ->
            codec.reconstructSummary(cleanBytes)
        }
    }

    override fun versionedSummarize(new: T, old: T?): String {
        return latestCodec.summarize(new, old)
    }
}
