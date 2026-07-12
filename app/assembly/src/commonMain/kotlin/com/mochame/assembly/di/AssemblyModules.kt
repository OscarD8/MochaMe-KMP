package com.mochame.assembly.di

import com.mochame.sync.di.SyncProductionModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        SyncProductionModule::class,
    ]
)
class ProductionAssembly

