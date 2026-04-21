package com.mochame.assembly.di

import com.mochame.assembly.IdentityBridge
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.orchestrator.BootStatusManager
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module

@Module
@ComponentScan(
    value = [
        "com.mochame.core.platform",
        "com.mochame.metadata",
        "com.mochame.sync.infrastructure"
    ]
)
class ProductionAssembly