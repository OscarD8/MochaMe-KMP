package com.mochame.utils.fixtures

import com.mochame.sync.api.hlc.HLC
import com.mochame.utils.implementations.DefaultMochaTimeUtils
import com.mochame.utils.interfaces.TimeUtils
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Defaults initial time to: Saturday, March 1, 2025 at 00:00:00 UTC.
 */
open class FakeTimeUtils(
    initialTime: Instant = HLC.APP_RELEASE_TIME.plus(1.days)
) : TimeUtils {

    private val lock = reentrantLock()
    private var currentTime: Instant = initialTime

    fun advanceTime(duration: Duration) = lock.withLock { currentTime += duration }
    fun reverseTime(duration: Duration) = lock.withLock { currentTime -= duration }
    fun setTime(instant: Instant) = lock.withLock { currentTime = instant }

    override fun now(): Instant = lock.withLock { currentTime }
}

class MochaFakeTimeUtils(
    val baseClock: FakeTimeUtils = FakeTimeUtils()
) : DefaultMochaTimeUtils(baseClock) {

    fun advanceTime(duration: Duration) = baseClock.advanceTime(duration)
    fun reverseTime(duration: Duration) = baseClock.reverseTime(duration)
    fun setTime(instant: Instant) = baseClock.setTime(instant)

    /**
     * Sets Clock instant based on base day
     *
     * @param baseDay Instant for test orientation. Defaults to August 27, 2026 (00:00:00 UTC).
     * @return [fakeClock.calculateMochaDay] equivalent
     */
    fun wind(baseDay: Long = 20693L): Long {
        val baseInstant = Instant.fromEpochSeconds(baseDay * 86_400L)
        setTime(baseInstant)
        return calculateMochaEpochDay(baseInstant)
    }
}