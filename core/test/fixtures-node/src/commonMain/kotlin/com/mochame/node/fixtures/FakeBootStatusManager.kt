package com.mochame.node.fixtures

import com.mochame.node.managers.DefaultBootStatusManager
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FakeBootStatusManager(
    initialState: BootState = BootState.Idle,
    timeout: Duration = 5.seconds,
    private val delegate: DefaultBootStatusManager = DefaultBootStatusManager(initialState, timeout)
) : BootStatusProvider, BootStatusUpdater by delegate {
    private val lock = reentrantLock()
    private val _history = mutableListOf(initialState)

    val history: List<BootState>
        get() = lock.withLock { _history.toList() }

    override fun updateBootState(newState: BootState) {
        lock.withLock {
            _history.add(newState)
        }
        delegate.updateBootState(newState)
    }

    fun reset(initialState: BootState = BootState.Idle) = lock.withLock {
        _history.clear()
        _history.add(initialState)
        delegate.updateBootState(initialState)
    }
}