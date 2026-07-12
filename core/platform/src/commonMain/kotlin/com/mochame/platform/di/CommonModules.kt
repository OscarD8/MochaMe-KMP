package com.mochame.platform.di

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import com.mochame.contract.di.AppScope
import com.mochame.contract.di.DefaultContext
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.MainContext
import com.mochame.logger.LoggerModule
import com.mochame.platform.providers.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
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
    @AppScope
    fun provideAppScope(@MainContext context: CoroutineContext): CoroutineScope =
        CoroutineScope(context + SupervisorJob())
}

/**
 * Expected module to be implemented by each platform (Android, Linux, JVM).
 */
@Module
expect class InternalPlatformModule


@Module(includes = [LoggerModule::class])
class PlatformDigestModule {

    @Single
    fun provideHasher(logger: Logger): Hasher = Hasher {
        createPlatformDigest(logger = logger)
    }
}