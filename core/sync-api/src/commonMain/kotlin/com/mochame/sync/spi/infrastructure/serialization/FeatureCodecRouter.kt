package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.infrastructure.VersionRouter

interface FeatureCodecRouter<T : LocalFirstEntity<T>, TCodec : Any> :
    VersionRouter<TCodec> {
    fun routedEncode(new: T, old: T?): ByteArray
    fun routedDecode(data: ByteArray, context: DecodeContext, existing: T?): T
    fun routedComputeChangedTags(new: T, old: T?): List<Int>
    fun routedReconstructSummary(data: ByteArray, context: DecodeContext): String
}
