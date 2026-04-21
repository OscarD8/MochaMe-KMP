package com.mochame.app.di.modules

import com.mochame.app.data.local.room.MochaDbOld
import com.mochame.app.data.local.room.getDatabaseBuilder
import com.mochame.app.data.local.room.getRoomDatabase
import com.mochame.app.di.providers.AppPaths
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.infrastructure.logging.LogTags
import com.mochame.app.infrastructure.utils.BufferProvider
import com.mochame.app.infrastructure.utils.JvmBufferProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

actual val platformModule = module {
    single<FileSystem> { SystemFileSystem }

    single<AppPaths> {
        val userHome = System.getProperty("user.home")
        val baseDir = "$userHome/.mochame"
        AppPaths(
            blobPending = "$baseDir/blobs/pending",
            blobCommitted = "$baseDir/blobs/committed",
            databasePath = "$baseDir/mocha_me.db"
        )
    }

    single<MochaDbOld> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(
            getDatabaseBuilder(paths = get()),
            dispatcherProvider = get()
        )
    }

    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

    single<BufferProvider> {
        JvmBufferProvider(
            logger = get {
                parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA)
            }
        )
    }

}