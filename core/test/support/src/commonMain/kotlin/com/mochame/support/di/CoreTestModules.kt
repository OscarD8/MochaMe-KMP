package com.mochame.support.di

import com.mochame.platform.providers.PlatformContext
import org.koin.dsl.module


val testContext = module {
    single<PlatformContext> { get() }
}


