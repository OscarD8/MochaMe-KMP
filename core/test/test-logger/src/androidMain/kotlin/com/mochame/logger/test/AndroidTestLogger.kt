package com.mochame.logger.test

import com.mochame.annotations.PlatformTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class PlatformTestTagModule {
    @Single
    @PlatformTag
    fun provideTag(): String =
        if (isHostTest()) TestTag.ANDROID_HOST else TestTag.ANDROID_DEVICE
}

private fun isHostTest(): Boolean {
    return System.getProperty("java.runtime.name")?.contains("Android") == false ||
            android.os.Build.FINGERPRINT == "robolectric"
}