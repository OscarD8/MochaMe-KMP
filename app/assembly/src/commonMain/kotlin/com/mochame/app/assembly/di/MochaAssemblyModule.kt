package com.mochame.app.assembly.di

import com.mochame.bio.di.BioProductionModule
import com.mochame.logger.LoggerModule
import com.mochame.node.di.NodeProductionModule
import com.mochame.sync.di.SyncProductionModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        LoggerModule::class,
        NodeProductionModule::class,
        MochaSchemaModule::class,
        BioProductionModule::class,
        SyncProductionModule::class,
    ]
)
@ComponentScan("com.mochame.app.assembly")
class MochaAssemblyModule