package com.mochame.utils.fixtures.di


import com.mochame.contract.providers.DateTimeProvider
import com.mochame.utils.fixtures.FakeDateTimeUtils
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
@Configuration
class FakeClockModule {
    @Single(binds = [DateTimeProvider::class, FakeDateTimeUtils::class])
    fun provideDateTimeUtils(): DateTimeProvider = FakeDateTimeUtils()
}

