package com.mochame.support

import androidx.sqlite.SQLiteDriver
import com.mochame.platform.providers.PlatformContext
import com.mochame.support.di.TestTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.di.PlatformTag
import org.koin.core.annotation.Configuration


@Module
@Configuration
actual class TestSupportModule {
    @Single
    @PlatformTag
    fun provideTag(): String = TestTag.LINUX_X64

    @Single
    fun provideDriver(): SQLiteDriver = BundledSQLiteDriver()

    @Single
    fun provideContext(): PlatformContext = PlatformContext()
}