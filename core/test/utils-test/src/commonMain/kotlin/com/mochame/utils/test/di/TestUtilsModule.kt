package com.mochame.utils.test.di


import com.mochame.utils.DateTimeUtils
import com.mochame.utils.test.FakeDateTimeUtils
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
class FakeClockModule {
    @Single
    fun provideDateTimeUtils(): DateTimeUtils = FakeDateTimeUtils()
}

