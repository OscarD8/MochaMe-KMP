package com.mochame.sync.internal.fixtures

import com.mochame.sync.spi.network.SendResult
import com.mochame.sync.spi.network.SyncTransport
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class FakeSyncTransport(
    initialConnected: Boolean = true,
    initialSendResult: Boolean = true
) : SyncTransport {

    private val lock = reentrantLock()

    private var _isConnected: Boolean = initialConnected
    private var _failWith: Exception? = null
    private var _sendResult: Boolean = initialSendResult
    private val _sentBatches = mutableListOf<SentBatch>()
    private val _connectCalls = mutableListOf<ConnectCall>()
    private var _pauseCallCount = 0
    private var _resumeCallCount = 0

    private var _inboundDeltaHandler: (suspend (watermark: Long, payload: ByteArray) -> Unit)? = null
    private var _inboundAckHandler: (suspend (batchId: Long, watermark: Long) -> Unit)? = null
    private var _onConnectedListener: (suspend () -> Unit)? = null
    private var _onDisconnectedListener: (suspend () -> Unit)? = null

    data class ConnectCall(val host: String, val port: Int, val groupId: String)
    data class SentBatch(val batchId: Long, val payload: ByteArray)

    override var isConnected: Boolean
        get() = lock.withLock { _isConnected }
        set(value) = lock.withLock { _isConnected = value }

    var failWith: Exception?
        get() = lock.withLock { _failWith }
        set(value) = lock.withLock { _failWith = value }

    var sendResult: Boolean
        get() = lock.withLock { _sendResult }
        set(value) = lock.withLock { _sendResult = value }

    val sentBatches: List<SentBatch>
        get() = lock.withLock { _sentBatches.map { it.copy(payload = it.payload.copyOf()) } }

    val sentPayloads: List<ByteArray>
        get() = lock.withLock { _sentBatches.map { it.payload.copyOf() } }

    val connectCalls: List<ConnectCall>
        get() = lock.withLock { _connectCalls.toList() }

    val pauseCallCount: Int
        get() = lock.withLock { _pauseCallCount }

    val resumeCallCount: Int
        get() = lock.withLock { _resumeCallCount }

    var autoAck: Boolean = false
    var nextAckWatermark: Long = 1L

    override suspend fun connect(host: String, port: Int, groupId: String) {
        val listener = lock.withLock {
            _connectCalls.add(ConnectCall(host, port, groupId))
            _isConnected = true
            _onConnectedListener
        }
        listener?.invoke()
    }

    override suspend fun send(batchId: Long, payload: ByteArray): SendResult = lock.withLock {
        _failWith?.let {
            _failWith = null
            return SendResult.Failure(it)
        }

        if (!_isConnected) {
            return SendResult.NoConnection
        }

        _sentBatches.add(SentBatch(batchId, payload.copyOf()))

        if (autoAck) {
            val ackHandler = _inboundAckHandler
            val watermark = nextAckWatermark++
            ackHandler?.let { handler ->
                handler(batchId, watermark)
            }
        }

        SendResult.Success
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

    override fun registerInboundDeltaHandler(onReceived: suspend (Long, ByteArray) -> Unit) {
        lock.withLock {
            _inboundDeltaHandler = onReceived
        }
    }

    override fun registerInboundAckHandler(onAck: suspend (batchId: Long, watermark: Long) -> Unit) {
        lock.withLock {
            _inboundAckHandler = onAck
        }
    }

    override fun setOnConnectedListener(onConnected: suspend () -> Unit) {
        lock.withLock {
            _onConnectedListener = onConnected
        }
    }

    override fun setOnDisconnectedListener(onDisconnected: suspend () -> Unit) {
        lock.withLock {
            _onDisconnectedListener = onDisconnected
        }
    }

    // --- Test Helpers ---

    suspend fun emitInboundDelta(watermark: Long, payload: ByteArray) {
        val handler = lock.withLock { _inboundDeltaHandler }
        handler?.invoke(watermark, payload)
    }

    suspend fun emitInbound(watermark: Long, payload: ByteArray) {
        emitInboundDelta(watermark, payload)
    }

    suspend fun emitInboundAck(batchId: Long, watermark: Long) {
        val handler = lock.withLock { _inboundAckHandler }
        handler?.invoke(batchId, watermark)
    }

    suspend fun triggerConnected() {
        val listener = lock.withLock { _onConnectedListener }
        listener?.invoke()
    }

    suspend fun triggerDisconnected() {
        val listener = lock.withLock {
            _isConnected = false
            _onDisconnectedListener
        }
        listener?.invoke()
    }

    fun reset() = lock.withLock {
        _sentBatches.clear()
        _connectCalls.clear()
        _pauseCallCount = 0
        _resumeCallCount = 0
        _isConnected = true
        _sendResult = true
        _failWith = null
        _inboundDeltaHandler = null
        _inboundAckHandler = null
        _onConnectedListener = null
        _onDisconnectedListener = null
    }
}