package com.mochame.sync.api.models

/**
 * Feature Implementations are to assign:
 * * [id]: ProtoNumber 1
 * * [isDeleted]: ProtoNumber 2
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