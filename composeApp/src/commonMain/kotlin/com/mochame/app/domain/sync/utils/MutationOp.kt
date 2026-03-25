package com.mochame.app.domain.sync.utils

enum class MutationOp(val id: Int) {
    UPSERT(0),
    DELETE(1);

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: UPSERT
    }
}
