package com.mochame.app.infrastructure.system.boot

import kotlinx.coroutines.flow.StateFlow

sealed class BootState {
    object Idle : BootState()
    object Initializing : BootState()
    object Ready : BootState()
    data class CriticalFailure(val error: String, val throwable: Throwable? = null) : BootState()
}