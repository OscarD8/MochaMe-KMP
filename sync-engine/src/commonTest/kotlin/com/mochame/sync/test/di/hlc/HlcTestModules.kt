package com.mochame.sync.test.di.hlc

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.sync.SyncInfraModule
import com.mochame.sync.infrastructure.HlcFactory
import com.mochame.utils.fixtures.FakeDateTimeUtils
import com.mochame.utils.fixtures.di.FakeClockModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [SyncHlcTestModule::class])
object HlcTestApp

@Module(
    includes = [
        FakeClockModule::class,
        SyncInfraModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.test.di.hlc")
class SyncHlcTestModule

@ExperimentalKermitApi
@Factory
data class HLCTestEnvironment(
    val factory: HlcFactory,
    val fakeClock: FakeDateTimeUtils,
    val writer: TestLogWriter
)