package com.mochame.node.managers

import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.spi.boot.BootStatusUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single


@Single(binds = [BootStatusProvider::class, BootStatusUpdater::class])
class DefaultBootStatusManager : BootStatusProvider, BootStatusUpdater {
    private val _state = MutableStateFlow<BootState>(BootState.Idle)

    override val bootState: StateFlow<BootState> = _state.asStateFlow()

    override fun updateBootState(newState: BootState) {
        _state.value = newState
    }
}