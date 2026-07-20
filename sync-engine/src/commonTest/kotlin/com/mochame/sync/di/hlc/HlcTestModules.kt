package com.mochame.sync.di.hlc

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.fakes.FakeHlcFactory
import com.mochame.sync.infrastructure.EngineHlcFactory
import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncHlcTestModule::class])
internal object HlcTestApp


@Module(
    includes = [
        FakeTimeProviderModule::class,
        SyncInfraModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.hlc")
internal class SyncHlcTestModule


@Module(includes = [FakeTimeProviderModule::class, TestLoggerModule::class])
internal class FakeHlcFactoryModule {
    @Single(binds = [HlcFactory::class, FakeHlcFactory::class])
    internal fun provideFakeHlcFactory(
        clock: FakeTimeProvider,
        logger: Logger
    ): FakeHlcFactory = FakeHlcFactory(clock, logger)
}

@ExperimentalKermitApi
@Factory
internal data class HLCTestEnvironment(
    val factory: EngineHlcFactory,
    val fakeClock: FakeTimeProvider,
    val writer: TestLogWriter
)