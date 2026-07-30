package com.mochame.sync.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class TriState(val dbValue: Int) {
    @ProtoNumber(0) UNSET(0),
    @ProtoNumber(1) FALSE(1),
    @ProtoNumber(2) TRUE(2);

    companion object {
        fun fromDb(value: Int): TriState = when (value) {
            1 -> FALSE
            2 -> TRUE
            else -> UNSET
        }
    }
}