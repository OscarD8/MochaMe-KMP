package com.mochame.support.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.core.providers.PlatformContext
import com.mochame.utils.DateTimeUtils
import com.mochame.support.fakes.FakeDateTimeUtils
import com.mochame.utils.logger.CleanLogWriter
import org.koin.core.qualifier.named
import org.koin.dsl.module


object TestTag {
    const val CORE = "CoreTest"
    const val JVM = "JVMTest"
    const val ANDROID_DEVICE = "AndroidDeviceTest"
    const val ANDROID_HOST = "AndroidHostTest"
    const val LINUX_X64 = "LinuxX64"
}


    // -----------------------------------------------------------
    // FAKES
    // -----------------------------------------------------------
    val fakeDateTimeUtilsModule = module {
        single<FakeDateTimeUtils> { FakeDateTimeUtils() }
        single<DateTimeUtils> { get<FakeDateTimeUtils>() }
    }

    @OptIn(ExperimentalKermitApi::class)
    fun testLoggingModule(
        platformTag: String = TestTag.JVM,
        minSeverity: Severity = Severity.Verbose
    ) = module {
        single<TestLogWriter> { TestLogWriter(minSeverity) }

        single<Logger>(named("RootLogger")) {
            Logger(
                config = StaticConfig(
                    logWriterList = listOf(
                        get<TestLogWriter>(),
                        CleanLogWriter(minSeverity),
                    )
                ),
                tag = platformTag
            )
        }
        factory { (domain: String, layer: String) ->
            val root = get<Logger>(named("RootLogger"))
            root.withTag("${root.tag} ❯ $layer ❯ $domain")
        }
    }

    val testContext = module {
        // This is actualized in the platform-specific test runners
        single<PlatformContext> { get() }
    }


