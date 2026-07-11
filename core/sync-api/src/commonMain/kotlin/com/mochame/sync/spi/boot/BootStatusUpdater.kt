package com.mochame.sync.spi.boot

import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider

interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}