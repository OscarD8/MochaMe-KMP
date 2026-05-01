package com.mochame.logger

import com.mochame.contract.di.PlatformTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformTagModule {
    @Single
    @PlatformTag
    fun providePlatformTag(): String = "Linux"
}