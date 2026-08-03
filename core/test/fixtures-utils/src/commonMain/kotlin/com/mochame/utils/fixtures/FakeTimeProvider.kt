package com.mochame.utils.fixtures

import com.mochame.sync.api.hlc.HLC
import com.mochame.utils.interfaces.TimeProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Defaults initial time to: Saturday, March 1, 2025 at 00:00:00 UTC.
 */
class FakeTimeProvider(
    initialTime: Instant = HLC.APP_RELEASE_TIME.plus(1.days)
) : TimeProvider {

    private val lock = reentrantLock()
    private var currentTime: Instant = initialTime

    fun advanceTime(duration: Duration) = lock.withLock { currentTime += duration }
    fun reverseTime(duration: Duration) = lock.withLock { currentTime -= duration }
    fun setTime(instant: Instant) = lock.withLock { currentTime = instant }

    override fun now(): Instant = lock.withLock { currentTime }
}