package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent

interface BatchCodec {
    fun encode(intents: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray): List<SyncIntent>
}
