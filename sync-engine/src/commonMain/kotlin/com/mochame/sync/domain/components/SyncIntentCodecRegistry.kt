package com.mochame.sync.domain.components

import com.mochame.sync.domain.model.SyncIntent

interface SyncIntentCodecRegistry {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}