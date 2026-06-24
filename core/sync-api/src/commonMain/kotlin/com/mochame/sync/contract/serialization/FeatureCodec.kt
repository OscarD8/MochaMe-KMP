package com.mochame.sync.contract.serialization

import com.mochame.contract.providers.BufferProvider
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.LocalFirstEntity

interface FeatureCodec<T : LocalFirstEntity<T>> {
    val bufferProvider: BufferProvider
    fun encode(new: T, old: T?): ByteArray?
    fun decode(bytes: ByteArray, context: DecodeContext): T
    fun reconstructSummary(bytes: ByteArray): String
    fun summarize(new: T, old: T?): String
}
