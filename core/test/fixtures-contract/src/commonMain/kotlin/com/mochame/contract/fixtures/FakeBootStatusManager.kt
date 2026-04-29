package com.mochame.contract.fixtures

import com.mochame.contract.boot.BootState
import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.boot.BootStatusUpdater
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