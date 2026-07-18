package com.mochame.sync.fakes

import co.touchlab.kermit.Logger
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.infrastructure.EngineHlcFactory
import com.mochame.utils.fixtures.FakeTimeProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class FakeHlcFactory(
    val clock: FakeTimeProvider ,
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

    // -- Read only ---
    val generatedHlcs: List<HLC>
        get() = lock.withLock { _generatedHlcs.toList() }

    val witnessedHlcs: List<HLC>
        get() = lock.withLock { _witnessedHlcs.toList() }

    val getNextHlcCallCount: Int
        get() = lock.withLock { _getNextHlcCallCount }


    fun reset() {
        _generatedHlcs.clear()
        _witnessedHlcs.clear()
        _getNextHlcCallCount = 0
    }

    // --- Delegated Production Logic - all suspending functionality held outside lock ---
    override suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC =
        realFactory.hydrate(lastKnownHlc, currentNodeId)

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