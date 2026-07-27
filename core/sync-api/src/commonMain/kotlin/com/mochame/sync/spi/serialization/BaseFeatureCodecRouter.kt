package com.mochame.sync.spi.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.models.DecodeContext

abstract class BaseFeatureCodecRouter<T : LocalFirstEntity<T>>(
    override val latestVersion: Int,
    override val versionRegistry: Array<FeatureCodec<T>?>,
    private val logger: Logger
) : FeatureCodecRouter<T, FeatureCodec<T>> {

    override fun routedEncode(new: T, old: T?): ByteArray? = latestCodec.encode(new, old)

    override fun routedDecode(data: ByteArray, context: DecodeContext): T =
        getCodec(context.featureSchemaVersion).decode(data, context)

    override fun routedReconstructSummary(
        data: ByteArray,
        context: DecodeContext
    ): String = getCodec(context.featureSchemaVersion).reconstructSummary(data)

    override fun routedSummarize(new: T, old: T?): String =
        latestCodec.summarize(new, old)

}
