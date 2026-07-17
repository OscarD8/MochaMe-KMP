package com.mochame.utils.fixtures

import com.mochame.utils.implementations.MochaDateTimeProvider
import com.mochame.utils.interfaces.DateTimeProvider
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.Instant

class FakeDateTimeUtils(initialMillis: Long = 1740787200000L) :
    MochaDateTimeProvider(), DateTimeProvider {

    private val lock = reentrantLock()

    private var _currentTime = initialMillis

    fun advanceTime(ms: Long) = lock.withLock {
        _currentTime += ms
    }

    fun reverseTime(ms: Long) = lock.withLock {
        _currentTime -= ms
    }

    fun setTime(ms: Long) = lock.withLock {
        _currentTime = ms
    }

    override fun now(): Instant = lock.withLock {
        Instant.fromEpochMilliseconds(_currentTime)
    }
}