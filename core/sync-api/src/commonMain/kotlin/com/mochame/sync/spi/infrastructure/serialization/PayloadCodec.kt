package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.spi.models.SyncIntent

interface PayloadCodec {
    fun encode(payload: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray) : List<SyncIntent>
}