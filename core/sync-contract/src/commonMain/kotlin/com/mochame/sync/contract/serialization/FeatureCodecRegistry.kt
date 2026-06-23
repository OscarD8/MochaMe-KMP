package com.mochame.sync.contract.serialization

import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity

interface FeatureCodecRegistry<T : LocalFirstEntity<T>> {
    fun encode(new: T, old: T?): ByteArray?
    fun decode(data: ByteArray, context: DecodeContext): T
    fun summarize(new: T, old: T?): String
    fun reconstructSummary(data: ByteArray): String
}
