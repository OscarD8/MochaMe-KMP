package com.mochame.support

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.di.PlatformTag
import com.mochame.platform.providers.PlatformContext
import com.mochame.support.di.TestTag
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
actual class TestSupportModule {
    @Single
    @PlatformTag
    fun provideTag(): String = TestTag.JVM

    @Single
    fun provideDriver(): SQLiteDriver = BundledSQLiteDriver()

    @Single
    fun provideContext(): PlatformContext = PlatformContext() // Your Linux implementation
}