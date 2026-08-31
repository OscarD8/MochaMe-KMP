package com.mochame.app.entry.linux

import com.mochame.app.assembly.di.MochaAssemblyModule
import com.mochame.bio.di.BioProductionModule
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin


@org.koin.core.annotation.KoinApplication(
    modules = [MochaAssemblyModule::class, LinuxCliModule::class]
)
class MochaCliApp

@Module(includes = [BioProductionModule::class, MochaAssemblyModule::class])
@ComponentScan("com.mochame.app.entry.linux")
class LinuxCliModule

fun initKoinCli(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin<MochaCliApp> {
        appDeclaration()
    }