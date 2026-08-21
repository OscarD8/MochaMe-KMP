package com.mochame.sync.di.hlc

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.sync.di.SyncDomainModule
import com.mochame.sync.domain.hlc.EngineHlcFactory
import com.mochame.utils.fixtures.FakeTimeUtils
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [EngineHlcTestModule::class])
internal object HlcTestApp


@Module(
    includes = [
        FakeTimeProviderModule::class,
        SyncDomainModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.hlc")
internal class EngineHlcTestModule


@ExperimentalKermitApi
@Factory
internal data class HLCTestEnvironment(
    val factory: EngineHlcFactory,
    val fakeClock: FakeTimeUtils,
    val writer: TestLogWriter
)