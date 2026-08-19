package com.mochame.sync.di.fixtures

import co.touchlab.kermit.Logger
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.internal.fixtures.SpyHlcFactory
import com.mochame.sync.internal.fixtures.SpySyncWorkerHook
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        FixturesPlatformModule::class,
        FakeTimeProviderModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.fixtures")
class SyncInternalFixturesModule {

    @Single(binds = [HlcFactory::class, SpyHlcFactory::class])
    internal fun provideSpyHlcFactory(
        clock: FakeTimeProvider,
        logger: Logger
    ): SpyHlcFactory = SpyHlcFactory(clock, logger)

    @Single(binds = [SyncIntentMaintenanceStore::class, SyncIntentStore::class])
    fun provideFakeSyncIntentStore(): FakeSyncIntentStore = FakeSyncIntentStore()

    @Single(binds = [SyncWorkerHook::class])
    fun provideSpySyncWorkerHook(): SpySyncWorkerHook = SpySyncWorkerHook()
}