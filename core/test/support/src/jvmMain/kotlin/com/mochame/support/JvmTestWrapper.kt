package com.mochame.support

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.platform.providers.PlatformContext
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class TestDependenciesModule {

    @Single
    fun provideDriver(): SQLiteDriver = BundledSQLiteDriver()

    @Single
    fun provideContext(): PlatformContext = PlatformContext() // Your Linux implementation
}

actual abstract class MochaPlatformTest actual constructor()