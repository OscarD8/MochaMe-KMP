package com.mochame.app.ui.di

import com.mochame.app.schema.di.MochaSchemaModule
import com.mochame.bio.ui.di.BioUiProductionModule
import com.mochame.logger.LoggerModule
import com.mochame.node.di.NodeProductionModule
import com.mochame.platform.di.PlatformProductionModule
import com.mochame.sync.di.SyncProductionModule
import com.mochame.utils.di.UtilsModule
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin


@org.koin.core.annotation.KoinApplication(
    modules = [
        MochaSchemaModule::class,

        LoggerModule::class,
        PlatformProductionModule::class,
        UtilsModule::class,

        SyncProductionModule::class,
        NodeProductionModule::class,

        BioUiProductionModule::class
    ]
)
@ComponentScan("com.mochame")
class MochaComposeApp

fun initKoinCompose(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin<MochaComposeApp> {
        appDeclaration()
    }