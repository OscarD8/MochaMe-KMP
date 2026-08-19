package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.spi.models.SyncIntent

interface BatchCodec {
    fun encode(intents: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray): List<SyncIntent>
}
