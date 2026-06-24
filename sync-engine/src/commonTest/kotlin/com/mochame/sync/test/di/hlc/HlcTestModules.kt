package com.mochame.sync.test.di.hlc

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.sync.SyncInfraModule
import com.mochame.sync.infrastructure.EngineHlcFactory
import com.mochame.sync.test.fakes.FakeHlcFactory
import com.mochame.utils.fixtures.FakeDateTimeUtils
import com.mochame.utils.fixtures.di.FakeClockModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncHlcUnitTestModule::class])
internal object HlcTestApp


@Module(
    includes = [
        FakeClockModule::class,
        SyncInfraModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.test.di.hlc")
internal class SyncHlcUnitTestModule


@Module(includes = [FakeClockModule::class, TestLoggerModule::class])
internal class FakeHlcFactoryModule {

    @Single
    internal fun provideFakeHlcFactory(
        clock: FakeDateTimeUtils,
        logger: Logger
    ): FakeHlcFactory = FakeHlcFactory(clock, logger)
}

@ExperimentalKermitApi
@Factory
internal data class HLCTestEnvironment(
    val factory: EngineHlcFactory,
    val fakeClock: FakeDateTimeUtils,
    val writer: TestLogWriter
)