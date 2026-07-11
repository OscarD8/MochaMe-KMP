package com.mochame.node

import com.mochame.contract.di.NodeManagerMutex
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
@ComponentScan("com.mochame.system.orchestrator")
class SystemOrchestratorModule {
    @Single
    @NodeManagerMutex
    fun provideIdentityMutex(): Mutex = Mutex()
}