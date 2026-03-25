package com.mochame.app.di.modules

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.mochame.app.infrastructure.logging.CleanLogWriter
import com.mochame.app.infrastructure.logging.LogTags
import com.mochame.app.infrastructure.logging.appendTag
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun loggingModule(platformTag: String) = module {
    // 1. The Root Logger is initialized with the Platform (e.g., "JVM")
    single<Logger>(named("RootLogger")) {
        val config = StaticConfig(logWriterList = listOf(CleanLogWriter()))
        Logger(config = config, tag = platformTag)
    }

    factory { (domain: String, layer: String) ->
        val root = get<Logger>(named("RootLogger"))
        // Path: Platform ❯ Layer ❯ Domain
        root.withTag("${root.tag} ❯ $layer ❯ $domain")
    }
}