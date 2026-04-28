package com.mochame.logger.test

import com.mochame.di.PlatformTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformTagModule {
    @Single
    @PlatformTag
    fun provideTag(): String = TestTag.JVM
}