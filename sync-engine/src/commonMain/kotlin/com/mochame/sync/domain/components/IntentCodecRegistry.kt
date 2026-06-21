package com.mochame.sync.domain.components

import com.mochame.sync.domain.model.SyncIntent

interface IntentCodecRegistry {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}