package com.mochame.sync.fakes

import co.touchlab.kermit.Logger
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.infrastructure.EngineHlcFactory
import com.mochame.utils.fixtures.FakeDateTimeUtils

class FakeHlcFactory(
    val clock: FakeDateTimeUtils = FakeDateTimeUtils(),
    private val logger: Logger
) : HlcFactory {

    private val realFactory = EngineHlcFactory(
        dateTimeUtils = clock,
        logger = logger
    )

    // --- Time Control ---
    fun advanceTime(ms: Long) = clock.advanceTime(ms)
    fun setTime(ms: Long) = clock.setTime(ms)

    // --- State Inspection ---
    val generatedHlcs = mutableListOf<HLC>()
    val witnessedHlcs = mutableListOf<HLC>()
    var getNextHlcCallCount = 0
        private set

    fun reset() {
        generatedHlcs.clear()
        witnessedHlcs.clear()
        getNextHlcCallCount = 0
    }

    // --- Delegated Production Logic ---
    override suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC =
        realFactory.hydrate(lastKnownHlc, currentNodeId)

    override suspend fun getNextHlc(): HLC =
        realFactory.getNextHlc().also {
            generatedHlcs.add(it)
            getNextHlcCallCount++
        }

    override suspend fun witness(remoteHlc: HLC) {
        witnessedHlcs.add(remoteHlc)
        realFactory.witness(remoteHlc)
    }

    override fun isValid(hlc: HLC): Boolean =
        realFactory.isValid(hlc)
}