package com.mochame.sync.domain.serialization

import com.mochame.sync.api.models.SyncIntent

interface PayloadCodec {
    fun encode(payload: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray) : List<SyncIntent>
}