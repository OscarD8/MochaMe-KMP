package com.mochame.metadata

import kotlinx.coroutines.flow.StateFlow


interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}

interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}
