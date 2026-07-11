package com.mochame.sync.domain.serialization

import com.mochame.sync.spi.serialization.VersionRouter
import com.mochame.sync.spi.models.SyncIntent

interface IntentCodecRouter: VersionRouter<IntentCodec> {
    fun routedEncode(intent: SyncIntent): ByteArray
    fun routedDecode(bytes: ByteArray, version: Int): SyncIntent
}
