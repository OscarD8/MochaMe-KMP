package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent


interface BatchCodecRegistry {
    fun encode(intents: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray): List<SyncIntent>
}
