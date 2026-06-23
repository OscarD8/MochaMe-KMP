package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent
import kotlinx.io.Buffer
import kotlinx.io.Source

interface IntentCodecRegistry {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}
