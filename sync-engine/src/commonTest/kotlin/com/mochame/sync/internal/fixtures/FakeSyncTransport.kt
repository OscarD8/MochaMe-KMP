package com.mochame.sync.internal.fixtures

import com.mochame.sync.spi.network.SyncTransport
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class FakeSyncTransport(
    initialConnected: Boolean = true,
    initialSendResult: Boolean = true
) : SyncTransport {

    private val lock = reentrantLock()

    private var _isConnected: Boolean = initialConnected
    private var _sendResult: Boolean = initialSendResult
    private val _sentPayloads = mutableListOf<ByteArray>()
    private val _connectCalls = mutableListOf<ConnectCall>()
    private var _pauseCallCount = 0
    private var _resumeCallCount = 0
    private var _inboundHandler: (suspend (Long, ByteArray) -> Unit)? = null
    private var _onConnectedListener: (suspend () -> Unit)? = null

    data class ConnectCall(val host: String, val port: Int, val groupId: String)

    override var isConnected: Boolean
        get() = lock.withLock { _isConnected }
        set(value) = lock.withLock { _isConnected = value }

    var sendResult: Boolean
        get() = lock.withLock { _sendResult }
        set(value) = lock.withLock { _sendResult = value }

    val sentPayloads: List<ByteArray>
        get() = lock.withLock { _sentPayloads.map { it.copyOf() } }

    val connectCalls: List<ConnectCall>
        get() = lock.withLock { _connectCalls.toList() }

    val pauseCallCount: Int
        get() = lock.withLock { _pauseCallCount }

    val resumeCallCount: Int
        get() = lock.withLock { _resumeCallCount }

    override suspend fun connect(host: String, port: Int, groupId: String) {
        val listener = lock.withLock {
            _connectCalls.add(ConnectCall(host, port, groupId))
            _isConnected = true
            _onConnectedListener
        }
        listener?.invoke()
    }

    override suspend fun send(payload: ByteArray): Boolean {
        val (canSend, result) = lock.withLock {
            if (!_isConnected) {
                false to false
            } else {
                _sentPayloads.add(payload.copyOf())
                true to _sendResult
            }
        }
        return canSend && result
    }

    override fun pause() {
        lock.withLock {
            _pauseCallCount++
            _isConnected = false
        }
    }

    override fun resume() {
        lock.withLock {
            _resumeCallCount++
            _isConnected = true
        }
    }

    override fun registerInboundHandler(onReceived: suspend (Long, ByteArray) -> Unit) {
        lock.withLock {
            _inboundHandler = onReceived
        }
    }

    override fun setOnConnectedListener(onConnected: suspend () -> Unit) {
        lock.withLock {
            _onConnectedListener = onConnected
        }
    }

    suspend fun emitInbound(watermark: Long, payload: ByteArray) {
        val handler = lock.withLock { _inboundHandler }
        handler?.invoke(watermark, payload)
    }

    suspend fun triggerConnected() {
        val listener = lock.withLock { _onConnectedListener }
        listener?.invoke()
    }

    fun reset() {
        lock.withLock {
            _sentPayloads.clear()
            _connectCalls.clear()
            _pauseCallCount = 0
            _resumeCallCount = 0
            _isConnected = true
            _sendResult = true
        }
    }
}