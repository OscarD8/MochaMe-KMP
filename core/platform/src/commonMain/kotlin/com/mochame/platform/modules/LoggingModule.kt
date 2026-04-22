package com.mochame.platform.modules

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.mochame.logger.CleanLogWriter
import org.koin.dsl.module

fun productionLoggingModule(platformTag: String) = module {
    single<Logger> {
        val config = StaticConfig(logWriterList = listOf(CleanLogWriter()))
        Logger(config = config, tag = platformTag)
    }
}