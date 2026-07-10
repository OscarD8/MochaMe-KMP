package com.mochame.sync.api.boot

import kotlinx.coroutines.flow.StateFlow


interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}

interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}
