package com.mochame.sync.di.codec

import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.di.FakeBufferProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [CodecTestModule::class])
internal class CodecTestApp

@Module(includes = [TestLoggerModule::class, FakeBufferProviderModule::class])
@ComponentScan( "com.mochame.sync.fixtures.serialization")
internal class CodecTestModule

