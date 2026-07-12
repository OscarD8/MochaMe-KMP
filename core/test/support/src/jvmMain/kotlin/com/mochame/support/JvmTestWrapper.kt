package com.mochame.support

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.platform.di.PlatformContext
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class TestTargetsProviderModule {
    @Single
    fun provideDriver(): SQLiteDriver = BundledSQLiteDriver()

    @Single
    fun provideContext(): PlatformContext = PlatformContext()
}

actual abstract class MochaPlatformTest actual constructor()