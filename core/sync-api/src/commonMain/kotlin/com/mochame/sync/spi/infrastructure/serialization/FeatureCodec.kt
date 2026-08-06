package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.infrastructure.BufferProvider

interface FeatureCodec<T : LocalFirstEntity<T>> {
    val bufferProvider: BufferProvider
    fun encode(new: T, old: T?): ByteArray?
    fun decode(bytes: ByteArray, context: DecodeContext, existing: T?): T
    fun reconstructSummary(bytes: ByteArray): String
    fun summarize(op: MutationOp, changedTags: List<Int>): String
    fun computeChangedTags(new: T, old: T?): List<Int>
}
