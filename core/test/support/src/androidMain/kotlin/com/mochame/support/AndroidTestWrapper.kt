package com.mochame.support

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mochame.di.PlatformTag
import com.mochame.platform.providers.PlatformContext
import com.mochame.support.di.TestTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
actual class SupportProviderModule {
    @Single
    fun provideDriver(): SQLiteDriver = if (isHostTest()) {
        AndroidSQLiteDriver()
    } else {
        BundledSQLiteDriver()
    }

    @Single
    @PlatformTag
    fun provideTag(): String =
        if (isHostTest()) TestTag.ANDROID_HOST else TestTag.ANDROID_DEVICE

    @Single
    fun provideContext(): PlatformContext =
        PlatformContext(ApplicationProvider.getApplicationContext())
}

private fun isHostTest(): Boolean {
    return System.getProperty("java.runtime.name")?.contains("Android") == false ||
            android.os.Build.FINGERPRINT == "robolectric"
}