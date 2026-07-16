package com.mochame.node.fixtures

import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBootStatusManager(
    initialState: BootState = BootState.Idle
) : BootStatusProvider, BootStatusUpdater {

    /**
     * Required as the context is non-suspending. In the risk of the runtime execution
     * being multithreaded for a test, this provides a lock that physically blocks other
     * Thread IDs from writing to the history/current [BootState] out of sequence.
     */
    private val lock = reentrantLock()

    private val _state = MutableStateFlow(initialState)
    override val bootState: StateFlow<BootState> = _state.asStateFlow()

    private val _history = mutableListOf<BootState>()
    val history: List<BootState>
        get() = lock.withLock { _history.toList() }

    init {
        lock.withLock {
            _history.add(initialState)
        }
    }

    override fun updateBootState(newState: BootState) {
        lock.withLock {
            _history.add(newState)
            _state.value = newState
        }
    }
}