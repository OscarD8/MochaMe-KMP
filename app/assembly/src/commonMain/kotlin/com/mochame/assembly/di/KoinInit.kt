package com.mochame.assembly.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration


fun initKoin(platformTag: String, appDeclaration: KoinAppDeclaration = {}) = startKoin {

    appDeclaration()

    modules(
    )
}