package com.mochame.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import org.koin.dsl.module


expect fun getPlatformTag(): String

val productionLoggingModule = module {
    single<Logger> {
        Logger(
            config = StaticConfig(
                logWriterList = listOf(CleanLogWriter())
            ),
            tag = getPlatformTag()
        )
    }
}