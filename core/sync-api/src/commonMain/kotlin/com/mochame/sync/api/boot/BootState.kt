package com.mochame.sync.api.boot

sealed class BootState {
    object Idle : BootState()
    object Initializing : BootState()
    object Ready : BootState()
    data class TransientFailure(val error: String, val exception: Exception? = null) : BootState()
    data class CriticalFailure(val error: String, val exception: Exception? = null) : BootState()
}