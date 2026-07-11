package com.mochame.node.fixtures

import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBootStatusManager(
    initialState: BootState = BootState.Idle
) : BootStatusProvider, BootStatusUpdater {

    private val _state = MutableStateFlow(initialState)
    override val bootState: StateFlow<BootState> = _state.asStateFlow()

    override fun updateBootState(newState: BootState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = BootState.Idle
    }
}