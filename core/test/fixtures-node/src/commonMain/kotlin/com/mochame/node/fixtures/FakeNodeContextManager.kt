package com.mochame.node.fixtures

import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.api.models.HLC
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.delay
import kotlin.time.Duration

class FakeNodeContextManager(
    private val defaultNodeId: String = "fake-node-uuid"
) : NodeContextManager {

    // "One lock to rule them all, one lock to find them, one lock to bring them all
    // and in the darkness bind them. No coroutine Mutex needed" - Gemini
    private val lock = reentrantLock()

    private var _seededContext: NodeContext? = null
    private var _forcedNextNodeId: String? = null

    private val _updatedHlcFloors = mutableListOf<HLC>()
    private val _recognizedServerResponses = mutableListOf<Pair<String, Long>>()
    private var _getOrEstablishCallCount = 0
    private var _setAppVersionCallCount = 0
    private var _simulatedDelay: Duration? = null

    // --- Synchronous Getters & Setters ---
    val updatedHlcFloors: List<HLC>
        get() = lock.withLock { _updatedHlcFloors.toList() }

    val recognizedServerResponses: List<Pair<String, Long>>
        get() = lock.withLock { _recognizedServerResponses.toList() }

    val getOrEstablishCallCount: Int
        get() = lock.withLock { _getOrEstablishCallCount }

    val setAppVersionCallCount: Int
        get() = lock.withLock { _setAppVersionCallCount }

    var seededContext: NodeContext?
        get() = lock.withLock { _seededContext }
        set(value) = lock.withLock { _seededContext = value }

    var simulatedDelay: Duration?
        get() = lock.withLock { _simulatedDelay }
        set(value) = lock.withLock { _simulatedDelay = value }

    var forcedNextNodeId: String?
        get() = lock.withLock { _forcedNextNodeId }
        set(value) = lock.withLock { _forcedNextNodeId = value }

    // Must be called inside a lock block
    private fun getOrInitializeLocked(baseVersion: Int = 0): NodeContext {
        val current = _seededContext ?: NodeContext(
            nodeId = _forcedNextNodeId ?: defaultNodeId,
            appVersion = baseVersion,
            lastServerSyncTime = null,
            maxHlc = null,
            lastServerWatermark = null,
            lastLocalMutationTime = null
        )
        _seededContext = current
        return current
    }

    fun reset() = lock.withLock {
        _seededContext = null
        _forcedNextNodeId = null
        _updatedHlcFloors.clear()
        _recognizedServerResponses.clear()
        _getOrEstablishCallCount = 0
        _setAppVersionCallCount = 0
        _simulatedDelay = null
    }

    // --- Interface Suspends ---

    override suspend fun getOrEstablishContext(baseVersion: Int): NodeContext {
        val delayDuration = lock.withLock {
            _getOrEstablishCallCount++
            _simulatedDelay
        }

        if (delayDuration != null) {
            delay(delayDuration)
        }

        return lock.withLock {
            getOrInitializeLocked(baseVersion)
        }
    }

    override suspend fun setAppVersion(targetVersion: Int) = lock.withLock {
        _setAppVersionCallCount++
        _seededContext =
            getOrInitializeLocked(targetVersion).copy(appVersion = targetVersion)
    }

    override suspend fun getLastBootedAppVersion(): Int? = lock.withLock {
        _seededContext?.appVersion
    }

    override suspend fun getLastServerSyncTime(): Long? = lock.withLock {
        _seededContext?.lastServerSyncTime
    }

    override suspend fun getLastLocalMutationTime(): Long? = lock.withLock {
        _seededContext?.lastLocalMutationTime
    }

    override suspend fun getNodeId(): String? = lock.withLock {
        _seededContext?.nodeId ?: _forcedNextNodeId ?: defaultNodeId
    }

    override suspend fun getMaxHlc(): HLC? = lock.withLock {
        _seededContext?.maxHlc
    }

    override suspend fun updateHlcFloor(hlc: HLC) = lock.withLock {
        _updatedHlcFloors.add(hlc)
        _seededContext = getOrInitializeLocked().copy(maxHlc = hlc)
    }

    override suspend fun overwriteNodeContext(nodeContext: NodeContext) = lock.withLock {
        _seededContext = nodeContext
    }

    override suspend fun recogniseServerResponse(watermark: String, timestamp: Long) =
        lock.withLock {
            _recognizedServerResponses.add(watermark to timestamp)
            _seededContext = getOrInitializeLocked().copy(
                lastServerWatermark = watermark,
                lastServerSyncTime = timestamp
            )
        }
}