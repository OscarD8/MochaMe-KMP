package com.mochame.app.infrastructure.system.boot


interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}