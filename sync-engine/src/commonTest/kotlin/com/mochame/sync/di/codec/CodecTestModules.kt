package com.mochame.sync.di.codec

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.di.FakeBufferProviderModule
import com.mochame.sync.fixtures.serialization.TestEntityCodecV1
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [EntityCodecTestModule::class])
internal class EntityCodecTestApp

@Module(includes = [TestLoggerModule::class, FakeBufferProviderModule::class])
@ComponentScan( "com.mochame.sync.fixtures.serialization")
internal class EntityCodecTestModule