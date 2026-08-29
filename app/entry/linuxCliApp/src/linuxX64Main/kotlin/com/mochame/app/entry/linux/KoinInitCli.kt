package com.mochame.app.entry.linux

import com.mochame.app.schema.di.MochaSchemaModule
import com.mochame.bio.di.BioProductionModule
import com.mochame.logger.LoggerModule
import com.mochame.node.di.NodeProductionModule
import com.mochame.platform.di.PlatformProductionModule
import com.mochame.sync.di.SyncProductionModule
import com.mochame.utils.di.UtilsModule
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin


@org.koin.core.annotation.KoinApplication
class MochaCliApp

@Module(
    includes =
        [
            LinuxCliModule::class,
            MochaSchemaModule::class,

            LoggerModule::class,
            PlatformProductionModule::class,
            UtilsModule::class,

            SyncProductionModule::class,
            NodeProductionModule::class,

            BioProductionModule::class
        ]
)
@ComponentScan("com.mochame.app.entry.linux")
class LinuxCliModule

fun initKoinCli(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin<MochaCliApp> {
        appDeclaration()
    }