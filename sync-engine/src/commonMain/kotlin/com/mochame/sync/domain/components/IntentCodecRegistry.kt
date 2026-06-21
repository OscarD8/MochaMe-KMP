package com.mochame.sync.domain.components

import com.mochame.sync.domain.model.SyncIntent

interface CodecRegistry<T> {
    fun encode(context: T): ByteArray
    fun decode(bytes: ByteArray): T
}