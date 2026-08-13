package com.mochame.sync.api.models

import com.mochame.sync.spi.infrastructure.serialization.FieldHlcMap

/**
 * Feature Implementations are to assign:
 * * [id]: ProtoNumber 1
 * * [isDeleted]: ProtoNumber 2
 *
 * Max Tag Count: 127 ([FieldHlcMap] stores the ProtoNumber as a single [Byte]).
 *
 * ```kotlin
 *     @ProtoNumber(1) override val id: Long,
 *     @ProtoNumber(2) override val isDeleted: Boolean? = null,
 * ```
 */
interface LocalFirstDelta {
    val id: Long
    val isDeleted: Boolean?
}