package com.mochame.app.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import org.koin.dsl.module

val loggingModule = module {
    // Provide a base configuration for Kermit
    single {
        val config = StaticConfig(
            logWriterList = listOf(platformLogWriter())
        )
        Logger(config = config, tag = "AppTag")
    }

    // Optional: Factory to allow injecting loggers with specific tags
    factory { (tag: String?) ->
        val baseLogger = get<Logger>()
        if (tag != null) baseLogger.withTag(tag) else baseLogger
    }
}