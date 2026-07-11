package com.mochame.utils.fixtures.di


import com.mochame.contract.providers.DateTimeProvider
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.utils.fixtures.FakeDateTimeUtils
import com.mochame.utils.fixtures.FakeIdGenerator
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

/**
 * Provides the fakes necessary to test IdentityManager logic.
 */
@Module
class FixtureUtilsModule {
    @Single(binds = [IdGenerator::class, FakeIdGenerator::class])
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()
}
