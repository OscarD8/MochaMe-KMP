package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent

interface IntentCodecRouter {
    fun versionedEncode(intent: SyncIntent): ByteArray
    fun versionedDecode(bytes: ByteArray): SyncIntent
}
