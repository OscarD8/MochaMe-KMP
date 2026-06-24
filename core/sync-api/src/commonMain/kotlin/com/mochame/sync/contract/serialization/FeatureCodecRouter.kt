package com.mochame.sync.contract.serialization

import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity

interface FeatureCodecRouter<T : LocalFirstEntity<T>, TCodec : Any> :
    VersionRouter<TCodec> {
    fun versionedEncode(new: T, old: T?): ByteArray?
    fun versionedDecode(data: ByteArray, context: DecodeContext): T
    fun versionedSummarize(new: T, old: T?): String
    fun versionedSummaryReconstruction(data: ByteArray): String
}
