package com.mochame.sync.domain.serialization

import com.mochame.sync.api.models.SyncIntent

interface IntentCodec {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}
