package com.mochame.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.mochame.contract.di.PlatformTag
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
expect class PlatformTagModule

@Module(includes = [PlatformTagModule::class])
class LoggerModule {

    @Single
    fun getLogger(@PlatformTag platformTag: String) : Logger = Logger(
        config = StaticConfig(
            logWriterList = listOf(CleanLogWriter())
        ),
        tag = platformTag
    )
}
