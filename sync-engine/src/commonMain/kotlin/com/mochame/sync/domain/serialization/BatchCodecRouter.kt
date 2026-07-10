package com.mochame.sync.domain.serialization

import com.mochame.sync.api.VersionRouter
import com.mochame.sync.api.models.SyncIntent


interface BatchCodecRouter: VersionRouter<BatchCodec> {
    fun routedEncode(intents: List<SyncIntent>): ByteArray
    fun routedDecode(bytes: ByteArray, version: Int): List<SyncIntent>
}
