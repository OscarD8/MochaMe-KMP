package com.mochame.sync.domain.serialization

import com.mochame.sync.contract.models.SyncIntent
import kotlinx.io.Buffer
import kotlinx.io.Source

interface BatchCodec {
    fun encode(intents: List<SyncIntent>): ByteArray
    fun decode(bytes: ByteArray): List<SyncIntent>
}
