package com.mochame.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import com.mochame.annotations.PlatformTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
expect class PlatformTagModule

@Module(includes = [PlatformTagModule::class])
class LoggerModule {

    @Single
    fun getLogger(@PlatformTag platformTag: String) : Logger = Logger(
        config = StaticConfig(
            minSeverity = Severity.Verbose,
            logWriterList = listOf(CleanLogWriter(minSeverity = Severity.Verbose))
        ),
        tag = platformTag
    )
}
