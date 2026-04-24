package com.mochame.assembly.di

import com.mochame.sync.SyncProductionModule
import org.koin.core.annotation.Module

@Module(
    includes = [
        SyncProductionModule::class,
    ]
)
class ProductionAssembly

