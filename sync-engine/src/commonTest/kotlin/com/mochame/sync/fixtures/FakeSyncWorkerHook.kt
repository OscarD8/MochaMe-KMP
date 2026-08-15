package com.mochame.sync.fixtures

import com.mochame.sync.infrastructure.DefaultSyncWorkerHook
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeSyncWorkerHook(
    private val delegate: SyncWorkerHook = DefaultSyncWorkerHook()
) : SyncWorkerHook {

    private val lock = reentrantLock()
    private var _invalidationCount = 0
    private var _totalDrains = 0
    private var pendingSignalError: Throwable? = null

    val invalidationCount: Int
        get() = lock.withLock { _invalidationCount }

    val totalDrains: Int
        get() = lock.withLock { _totalDrains }

    override val signals: Flow<Unit> = delegate.signals.map { signal ->
        val errorToThrow = lock.withLock {
            _totalDrains++
            val error = pendingSignalError
            pendingSignalError = null
            error
        }
        errorToThrow?.let { throw it }
        signal
    }

    fun throwOnNextSignal(throwable: Throwable) = lock.withLock {
        pendingSignalError = throwable
    }

    fun reset() = lock.withLock {
        _invalidationCount = 0
        _totalDrains = 0
        pendingSignalError = null
    }

    override fun invalidate() {
        lock.withLock { _invalidationCount++ }
        delegate.invalidate()
    }
}