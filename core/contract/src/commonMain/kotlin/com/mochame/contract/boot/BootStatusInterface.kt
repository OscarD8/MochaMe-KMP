package com.mochame.contract.boot

import kotlinx.coroutines.flow.StateFlow


interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}

interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}
