package com.mochame.support

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochame.platform.providers.PlatformContext
import org.junit.runner.RunWith
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


/**
 * * @RunWith(AndroidJUnit4::class) intelligently delegates:
 * - On JVM (Host): Routes to RobolectricTestRunner.
 * - On Device (ART): Routes to InstrumentationRunner.
 */
@RunWith(AndroidJUnit4::class)
actual abstract class MochaPlatformTest actual constructor()

@Module
actual class TestDependenciesModule {
    @Single
    fun provideDriver(): SQLiteDriver = if (isHostTest()) {
        AndroidSQLiteDriver()
    } else {
        BundledSQLiteDriver()
    }


    @Single
    fun provideContext(): PlatformContext =
        PlatformContext(ApplicationProvider.getApplicationContext())
}

private fun isHostTest(): Boolean {
    return System.getProperty("java.runtime.name")?.contains("Android") == false ||
            android.os.Build.FINGERPRINT == "robolectric"
}