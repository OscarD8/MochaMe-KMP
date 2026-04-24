package com.mochame.support.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.di.PlatformTag
import com.mochame.logger.CleanLogWriter
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


object TestTag {
    const val CORE = "CoreTest"
    const val JVM = "JVMTest"
    const val ANDROID_DEVICE = "AndroidDeviceTest"
    const val ANDROID_HOST = "AndroidHostTest"
    const val LINUX_X64 = "LinuxX64Test"
}

@OptIn(ExperimentalKermitApi::class)
@Module
@Configuration
class TestLoggerModule {

    @Single
    fun provideTestLogWriter(): TestLogWriter = TestLogWriter(Severity.Verbose)

    @Single
    fun provideTestLogger(
        writer: TestLogWriter,
        @PlatformTag tag: String
    ): Logger = Logger(
        config = StaticConfig(
            logWriterList = listOf(
                writer,
                CleanLogWriter(Severity.Verbose)
            )
        ),
        tag = tag
    )
}


