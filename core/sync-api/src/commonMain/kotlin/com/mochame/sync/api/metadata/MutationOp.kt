package com.mochame.sync.api.metadata

import kotlinx.serialization.Serializable

/**
 * New entries must append to the existing structure. Never adjust existing ordinal state.
 */
@Serializable
enum class MutationOp(val id: Int) {
    UPSERT(0),
    DELETE(1),
    UNKNOWN(2);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: UNKNOWN

        fun safeValueOf(value: String): MutationOp {
            return entries.firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}