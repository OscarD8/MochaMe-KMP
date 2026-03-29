package com.mochame.app.infrastructure.system.boot

sealed class BootState {
    object Idle : BootState()
    object Initializing : BootState()
    object Ready : BootState()
    data class TransientFailure(val error: String, val throwable: Throwable? = null) : BootState()
    data class CriticalFailure(val error: String, val throwable: Throwable? = null) : BootState()
}