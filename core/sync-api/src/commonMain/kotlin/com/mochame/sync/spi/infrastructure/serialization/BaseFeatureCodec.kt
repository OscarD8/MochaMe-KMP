package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.infrastructure.BufferProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Internal Base Class: Enforces standard delta logic.
 *
 * * [T] = Main Domain Entity (e.g. DailyContext)
 * * [D] = Serializable Protobuf Delta Schema (e.g. DailyContextDeltaV1)
 */
abstract class BaseFeatureCodec<T : LocalFirstEntity<T>, D : Any>(
    override val bufferProvider: BufferProvider,
    private val deltaSerializer: KSerializer<D>
) : FeatureCodec<T> {

    @OptIn(ExperimentalSerializationApi::class)
    override fun encode(new: T, old: T?): ByteArray? {
        val deltaPayload = when {
            new.isDeleted -> buildDeleteDelta(new)
            old == null -> buildInsertDelta(new)
            else -> buildUpdateDelta(new, old) ?: return null
        }

        return ProtoBuf.encodeToByteArray(deltaSerializer, deltaPayload)
    }

    protected abstract fun buildDeleteDelta(entity: T): D
    protected abstract fun buildInsertDelta(entity: T): D
    protected abstract fun buildUpdateDelta(new: T, old: T): D?
}