package com.mochame.core.modules

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.mochame.utils.logger.CleanLogWriter
import org.koin.dsl.module

fun productionLoggingModule(platformTag: String) = module {
    single<Logger> {
        val config = StaticConfig(logWriterList = listOf(CleanLogWriter()))
        Logger(config = config, tag = platformTag)
    }
}