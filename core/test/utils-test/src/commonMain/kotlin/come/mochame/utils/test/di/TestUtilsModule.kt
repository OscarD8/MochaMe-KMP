package come.mochame.utils.test.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.utils.DateTimeUtils
import com.mochame.logger.CleanLogWriter
import come.mochame.utils.test.FakeDateTimeUtils
import org.koin.core.annotation.Module
import org.koin.dsl.module

object TestTag {
    const val CORE = "CoreTest"
    const val JVM = "JVMTest"
    const val ANDROID_DEVICE = "AndroidDeviceTest"
    const val ANDROID_HOST = "AndroidHostTest"
    const val LINUX_X64 = "LinuxX64"
}

@OptIn(ExperimentalKermitApi::class)
fun testLoggingModule(
    platformTag: String = TestTag.JVM,
    minSeverity: Severity = Severity.Verbose
) = module {
    single<TestLogWriter> { TestLogWriter(minSeverity) }

    single<Logger> {
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
}

/**
 * Simple fake clock.
 */
@Module
class FakeClockModule {
    val definitions = module {
        single<DateTimeUtils> { FakeDateTimeUtils() }
    }
}

