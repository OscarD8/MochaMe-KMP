package com.mochame.app.domain.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BootStatusManager : BootStatusProvider, BootStatusUpdater {
    private val _state = MutableStateFlow<BootState>(BootState.Idle)

    override val bootState: StateFlow<BootState> = _state.asStateFlow()

    override fun updateBootState(newState: BootState) {
        _state.value = newState
    }
}