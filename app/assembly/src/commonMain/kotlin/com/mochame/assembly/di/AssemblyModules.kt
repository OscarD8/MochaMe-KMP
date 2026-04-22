package com.mochame.assembly.di

import com.mochame.sync.di.SyncEngineModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        SyncEngineModule::class,

    ]
)
class ProductionAssembly

