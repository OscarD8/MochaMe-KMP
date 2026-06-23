package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent
import kotlinx.io.Source

interface IntentCodec {
    fun encode(intent: SyncIntent): ByteArray
    fun decode(bytes: ByteArray): SyncIntent
}
