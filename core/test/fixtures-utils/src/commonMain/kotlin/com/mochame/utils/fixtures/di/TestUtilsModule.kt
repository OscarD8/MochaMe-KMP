package com.mochame.utils.fixtures.di


import com.mochame.utils.DateTimeUtils
import com.mochame.utils.fixtures.FakeDateTimeUtils
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

