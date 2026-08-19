package com.mochame.utils.fixtures.di


import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.MochaFakeTimeProvider
import com.mochame.utils.interfaces.MochaTimeProvider
import com.mochame.utils.interfaces.TimeProvider
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
class FakeTimeProviderModule {
    @Single(binds = [TimeProvider::class, FakeTimeProvider::class])
    fun provideFakeTimeUtils(): FakeTimeProvider = FakeTimeProvider()
}

@Module(includes = [FakeTimeProviderModule::class])
class FakeMochaTimeProviderModule {
    @Single(binds = [MochaTimeProvider::class])
    fun provideFakeMochaTimeProvider(baseClock: FakeTimeProvider): MochaFakeTimeProvider =
        MochaFakeTimeProvider(baseClock)
}