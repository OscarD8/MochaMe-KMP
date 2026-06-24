package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent


interface BatchCodecRouter {
    fun versionEncode(intents: List<SyncIntent>): ByteArray
    fun versionedDecode(bytes: ByteArray): List<SyncIntent>
}
