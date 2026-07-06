package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.models.SyncIntent

interface IntentCodecRouter: VersionRouter<IntentCodec> {
    fun routedEncode(intent: SyncIntent): ByteArray
    fun routedDecode(bytes: ByteArray, version: Int): SyncIntent
}
