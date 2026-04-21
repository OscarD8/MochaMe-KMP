package com.mochame.sync.domain

import com.mochame.metadata.BootState
import kotlinx.coroutines.flow.StateFlow


interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}

interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}
