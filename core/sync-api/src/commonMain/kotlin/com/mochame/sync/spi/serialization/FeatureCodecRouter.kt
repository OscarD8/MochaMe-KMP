package com.mochame.sync.spi.serialization

import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.api.models.LocalFirstEntity

interface FeatureCodecRouter<T : LocalFirstEntity<T>, TCodec : Any> :
    VersionRouter<TCodec> {
    fun routedEncode(new: T, old: T?): ByteArray?
    fun routedDecode(data: ByteArray, context: DecodeContext): T
    fun versionedSummarize(new: T, old: T?): String
    fun versionedSummaryReconstruction(data: ByteArray): String
}
