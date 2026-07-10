package com.mochame.sync.domain.serialization

import com.mochame.sync.api.VersionRouter
import com.mochame.sync.api.models.SyncIntent

interface IntentCodecRouter: VersionRouter<IntentCodec> {
    fun routedEncode(intent: SyncIntent): ByteArray
    fun routedDecode(bytes: ByteArray, version: Int): SyncIntent
}
