package com.mochame.utils.fixtures.di


import com.mochame.utils.fixtures.FakeTimeUtils
import com.mochame.utils.fixtures.MochaFakeTimeUtils
import com.mochame.utils.interfaces.MochaTimeUtils
import com.mochame.utils.interfaces.TimeUtils
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
class FakeTimeProviderModule {
    @Single(binds = [TimeUtils::class, FakeTimeUtils::class])
    fun provideFakeTimeUtils(): FakeTimeUtils = FakeTimeUtils()
}

@Module(includes = [FakeTimeProviderModule::class])
class FakeMochaTimeProviderModule {
    @Single(binds = [MochaTimeUtils::class])
    fun provideFakeMochaTimeProvider(baseClock: FakeTimeUtils): MochaFakeTimeUtils =
        MochaFakeTimeUtils(baseClock)
}