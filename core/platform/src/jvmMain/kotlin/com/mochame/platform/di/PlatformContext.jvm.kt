package com.mochame.platform.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformContext {
    @Single
    fun providePlatformContext(): PlatformContext =
        com.mochame.platform.di.PlatformContext()
}