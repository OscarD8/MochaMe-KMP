package com.mochame.sync.domain.components

import com.mochame.sync.domain.model.SyncIntent

interface SyncBatchCodecRegistry {
    fun encode(intents: List<SyncIntent>): ByteArray

    /**
     * Decode a ByteArray from the server to
     */
    fun decode(bytes: ByteArray): List<SyncIntent>
}