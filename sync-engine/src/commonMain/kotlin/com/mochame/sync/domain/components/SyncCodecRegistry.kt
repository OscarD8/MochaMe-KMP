package com.mochame.sync.domain.components

import com.mochame.sync.domain.model.SyncIntent

interface SyncCodecRegistry {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
    fun validate(bytes: ByteArray) : Boolean
}