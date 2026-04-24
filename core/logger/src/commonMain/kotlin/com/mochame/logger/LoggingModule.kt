package com.mochame.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.mochame.di.PlatformTag
import org.koin.core.annotation.Single


@Single
fun getRootLogger(
    @PlatformTag platformTag: String
): Logger = Logger(
    config = StaticConfig(
        logWriterList = listOf(CleanLogWriter())
    ),
    tag = platformTag
)

