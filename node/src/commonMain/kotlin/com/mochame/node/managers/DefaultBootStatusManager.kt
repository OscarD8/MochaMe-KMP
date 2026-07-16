package com.mochame.node.managers

import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * Single component that binds to both provider and updater interfaces for the
 * boot state of a node. This component simply provides read or write access to the
 * mutable state flow, with no strict enforcing of state transition order. It is down
 * to the caller to manage the transitional lifecycle themselves.
 * If it happens that the components dependent on provider/updater interfaces becomes
 * more complex, it may be necessary to update this component to be more of a strict
 * state machine.
 */
@Single(binds = [BootStatusProvider::class, BootStatusUpdater::class])
class DefaultBootStatusManager : BootStatusProvider, BootStatusUpdater {
    private val _state = MutableStateFlow<BootState>(BootState.Idle)

    override val bootState: StateFlow<BootState> = _state.asStateFlow()

    override fun updateBootState(newState: BootState) {
        _state.value = newState
    }
}