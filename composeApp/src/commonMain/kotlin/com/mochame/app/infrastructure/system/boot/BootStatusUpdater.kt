package com.mochame.app.infrastructure.system.boot


// What the Janitor sees
interface BootStatusUpdater : BootStatusProvider {
    fun updateBootState(newState: BootState)
}