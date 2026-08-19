package com.mochame.sync.spi.infrastructure.serialization

import com.mochame.sync.spi.models.SyncIntent

interface IntentCodec {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}
