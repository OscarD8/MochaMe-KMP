package com.mochame.sync.spi.serialization

import com.mochame.contract.providers.BufferProvider
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.api.models.LocalFirstEntity

interface FeatureCodec<T : LocalFirstEntity<T>> {
    val bufferProvider: BufferProvider
    fun encode(new: T, old: T?): ByteArray?
    fun decode(bytes: ByteArray, context: DecodeContext): T
    fun reconstructSummary(bytes: ByteArray): String
    fun summarize(new: T, old: T?): String
}
