package com.mochame.metadata

sealed class BootState {
    object Idle : BootState()
    object Initializing : BootState()
    object Ready : BootState()
    data class TransientFailure(val error: String, val throwable: Throwable? = null) : BootState()
    data class CriticalFailure(val error: String, val throwable: Throwable? = null) : BootState()
}