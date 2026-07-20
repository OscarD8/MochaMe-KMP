package com.mochame.sync.fakes

import co.touchlab.kermit.Logger
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.infrastructure.EngineHlcFactory
import com.mochame.utils.fixtures.FakeTimeProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class FakeHlcFactory(
    clock: FakeTimeProvider,
    private val logger: Logger
) : HlcFactory {

    private val lock = reentrantLock()

    private val realFactory = EngineHlcFactory(
        dateTimeUtils = clock,
        logger = logger
    )

    // --- Backing Fields ---
    private val _generatedHlcs = mutableListOf<HLC>()
    private val _witnessedHlcs = mutableListOf<HLC>()
    private var _getNextHlcCallCount = 0

    // Hydration tracking
    private var _hydrateCallCount = 0
    private var _lastHydratedHlc: HLC? = null
    private var _lastHydratedNodeId: String? = null

    // --- Read-Only Properties ---
    val generatedHlcs: List<HLC>
        get() = lock.withLock { _generatedHlcs.toList() }

    val witnessedHlcs: List<HLC>
        get() = lock.withLock { _witnessedHlcs.toList() }

    val getNextHlcCallCount: Int
        get() = lock.withLock { _getNextHlcCallCount }

    val hydrateCallCount: Int
        get() = lock.withLock { _hydrateCallCount }

    val lastHydratedHlc: HLC?
        get() = lock.withLock { _lastHydratedHlc }

    val lastHydratedNodeId: String?
        get() = lock.withLock { _lastHydratedNodeId }

    fun reset() = lock.withLock {
        _generatedHlcs.clear()
        _witnessedHlcs.clear()
        _getNextHlcCallCount = 0
        _hydrateCallCount = 0
        _lastHydratedHlc = null
        _lastHydratedNodeId = null
    }

    override suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC {
        lock.withLock {
            _hydrateCallCount++
            _lastHydratedHlc = lastKnownHlc
            _lastHydratedNodeId = currentNodeId
        }
        return realFactory.hydrate(lastKnownHlc, currentNodeId)
    }

    override suspend fun getNextHlc(): HLC {
        val nextHlc = realFactory.getNextHlc()

        lock.withLock {
            _generatedHlcs.add(nextHlc)
            _getNextHlcCallCount++
        }
        return nextHlc
    }

    override suspend fun witness(remoteHlc: HLC) {
        lock.withLock {
            _witnessedHlcs.add(remoteHlc)
        }

        realFactory.witness(remoteHlc)
    }

    override fun isValid(hlc: HLC): Boolean =
        realFactory.isValid(hlc)
}