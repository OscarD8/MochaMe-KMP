package com.mochame.system.orchestrator

import com.mochame.contract.di.IdentityMutex
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
@ComponentScan("com.mochame.system.orchestrator")
class SystemOrchestratorModule {
    @Single
    @IdentityMutex
    fun provideIdentityMutex(): Mutex = Mutex()
}