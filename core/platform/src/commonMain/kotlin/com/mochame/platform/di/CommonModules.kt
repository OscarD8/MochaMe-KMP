package com.mochame.platform.di

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.annotations.DefaultContext
import com.mochame.annotations.IoContext
import com.mochame.annotations.MainContext
import com.mochame.logger.LoggerModule
import com.mochame.platform.providers.createPlatformDigest
import com.mochame.sync.spi.infrastructure.DigestFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Module(includes = [InternalPlatformModule::class])
class CommonPlatformModule {

    @Single
    fun provideProductionDriver(): SQLiteDriver = BundledSQLiteDriver()

    @Single
    @IoContext
    fun provideIoContext(): CoroutineContext = Dispatchers.IO

    @Single
    @MainContext
    fun provideMainContext(): CoroutineContext = Dispatchers.Main

    @Single
    @DefaultContext
    fun provideDefaultContext(): CoroutineContext = Dispatchers.Default

    @Single
    @AppBackgroundScope
    fun provideBackgroundAppScope(@DefaultContext context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + SupervisorJob())

    @Single
    fun provideHasher(logger: Logger): DigestFactory = DigestFactory {
        createPlatformDigest(logger = logger)
    }
}

/**
 * Expected module to be implemented by each platform (Android, Linux, JVM).
 */
@Module
expect class InternalPlatformModule

@Module(includes = [LoggerModule::class])
@ComponentScan("com.mochame.platform.providers")
class PlatformProviderModule