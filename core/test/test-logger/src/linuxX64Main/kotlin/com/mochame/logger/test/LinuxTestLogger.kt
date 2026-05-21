package com.mochame.logger.test

import com.mochame.contract.di.PlatformTag
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformTestTagModule {
    @Single
    @PlatformTag
    fun provideTag(): String = TestTag.LINUX_X64
}