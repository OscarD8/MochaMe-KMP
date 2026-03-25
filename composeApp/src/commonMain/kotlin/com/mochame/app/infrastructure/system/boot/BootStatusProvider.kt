package com.mochame.app.infrastructure.system.boot

import kotlinx.coroutines.flow.StateFlow

// What the ViewModels see
interface BootStatusProvider {
    val bootState: StateFlow<BootState>
}
