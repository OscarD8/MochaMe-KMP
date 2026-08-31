package com.mochame.app.ui.di

import com.mochame.app.assembly.di.MochaAssemblyModule
import com.mochame.bio.ui.di.BioUiProductionModule
import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin


@org.koin.core.annotation.KoinApplication(
    modules = [MochaAssemblyModule::class, BioUiProductionModule::class]
)
class MochaComposeApp

fun initKoinCompose(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin<MochaComposeApp> {
        appDeclaration()
    }