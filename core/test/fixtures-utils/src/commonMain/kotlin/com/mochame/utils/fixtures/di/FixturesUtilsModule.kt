package com.mochame.utils.fixtures.di


import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.interfaces.TimeProvider
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
@Configuration
class FakeTimeProviderModule {
    @Single(binds = [TimeProvider::class, FakeTimeProvider::class])
    fun provideFakeTimeUtils(): FakeTimeProvider = FakeTimeProvider()
}

