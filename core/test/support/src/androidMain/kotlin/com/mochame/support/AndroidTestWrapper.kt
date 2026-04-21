package com.mochame.support

import android.content.Context
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mochame.core.providers.PlatformContext
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

actual fun getPlatformTestDependencies(testContext: CoroutineContext): Module = module {
    val isHostTest =
        System.getProperty("java.runtime.name")?.contains("Android") == false ||
                android.os.Build.FINGERPRINT == "robolectric"

    if (isHostTest) {
        single<SQLiteDriver> { AndroidSQLiteDriver() }
    } else {
        single<SQLiteDriver> { BundledSQLiteDriver() }
    }

    single<PlatformContext> { ApplicationProvider.getApplicationContext() }
}