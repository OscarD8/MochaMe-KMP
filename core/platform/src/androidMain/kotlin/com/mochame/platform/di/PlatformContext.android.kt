package com.mochame.platform.di

import android.content.Context
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformContext(val androidContext: Context) {
    @Single
    fun provideContext(androidContext: Context): PlatformContext =
        com.mochame.platform.di.PlatformContext(androidContext)
}