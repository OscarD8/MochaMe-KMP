package com.mochame.app.domain.system

import kotlinx.coroutines.flow.StateFlow

sealed class BootState {
    object Idle : BootState()
    object Initializing : BootState()
    object Ready : BootState()
    data class CriticalFailure(val error: String, val throwable: Throwable? = null) : BootState()
}

// What the ViewModels see
interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}

// What the Janitor sees
interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}