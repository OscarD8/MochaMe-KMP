package com.mochame.sync.api.models

/**
 * When a concrete class implements this contract, it must declare itself as the
 * type parameter T. Can be thought of a compiler contract where T
 * becomes your models type, meaning manual casting by the developer is not required
 * for all generic local first sync manipulations on a given model.
 *
 * The intended application is for all feature models that exist in this
 * distributed system to implement this contract.
 *
 * To understand this deeper, look into the bytecode adjustments and background
 * checkcast instructions that must be the result of this compiler token system...
 */
interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: HLC // This replaced lastModified
    fun withHlc(hlc: HLC): T
    fun withPhysicalTime(time: Long): T
}