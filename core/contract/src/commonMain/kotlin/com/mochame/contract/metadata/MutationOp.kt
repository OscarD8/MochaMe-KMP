package com.mochame.contract.metadata

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