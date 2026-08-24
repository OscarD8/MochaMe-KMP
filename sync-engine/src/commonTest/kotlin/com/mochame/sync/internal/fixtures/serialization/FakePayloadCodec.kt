package com.mochame.sync.internal.fixtures.serialization

import com.mochame.sync.spi.infrastructure.serialization.PayloadCodec
import com.mochame.sync.spi.models.SyncIntent
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock


class FakePayloadCodec : PayloadCodec {
    private val lock = reentrantLock()

    var encodeError: Throwable? = null
    var decodeError: Throwable? = null
    var nextDecodeResult: List<SyncIntent> = emptyList()

    private val _encodedInvocations = mutableListOf<List<SyncIntent>>()
    private val _decodedInvocations = mutableListOf<ByteArray>()

    val encodedInvocations: List<List<SyncIntent>>
        get() = lock.withLock { _encodedInvocations.toList() }

    val decodedInvocations: List<ByteArray>
        get() = lock.withLock { _decodedInvocations.toList() }

    val encodeCallCount: Int get() = lock.withLock { _encodedInvocations.size }
    val decodeCallCount: Int get() = lock.withLock { _decodedInvocations.size }

    override fun encode(payload: List<SyncIntent>): ByteArray = lock.withLock {
        _encodedInvocations.add(payload)
        encodeError?.let { throw it }
        byteArrayOf(0x01)
    }

    override fun decode(bytes: ByteArray): List<SyncIntent> = lock.withLock {
        _decodedInvocations.add(bytes)
        decodeError?.let { throw it }
        nextDecodeResult
    }

    fun reset() = lock.withLock {
        encodeError = null
        decodeError = null
        nextDecodeResult = emptyList()
        _encodedInvocations.clear()
        _decodedInvocations.clear()
    }
}