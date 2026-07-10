package com.mochame.sync.api.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.api.getCodec
import com.mochame.sync.api.latestCodec
import com.mochame.sync.api.models.DecodeContext
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.api.stripAndVersion

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
