package com.mochame.sync.api.boot

sealed class BootState {
    object Idle : BootState() {
        override fun toString() = "Idle"
    }
    object Initializing : BootState() {
        override fun toString() = "Init"
    }
    object Ready : BootState() {
        override fun toString() = "Ready"
    }
    data class TransientFailure(val error: String, val exception: Exception? = null) : BootState()
    data class CriticalFailure(val error: String, val exception: Exception? = null) : BootState()
}