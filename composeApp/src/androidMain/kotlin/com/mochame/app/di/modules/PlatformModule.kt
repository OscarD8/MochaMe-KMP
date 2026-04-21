package com.mochame.app.di.modules

import android.content.Context
import com.mochame.app.data.local.room.MochaDbOld
import com.mochame.app.data.local.room.getDatabaseBuilder
import com.mochame.app.data.local.room.getRoomDatabase
import com.mochame.app.di.providers.AppPaths
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.infrastructure.logging.LogTags
import com.mochame.app.infrastructure.utils.AndroidBufferProvider
import com.mochame.app.infrastructure.utils.BufferProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

actual val platformModule = module {
    single<FileSystem> { SystemFileSystem }

    single<AppPaths> {
        val ctx = get<Context>()
        val baseDir = ctx.filesDir.absolutePath
        AppPaths(
            blobPending = "$baseDir/blobs/pending",
            blobCommitted = "$baseDir/blobs/committed",
            databasePath = "$baseDir/mocha.db"
        )
    }

    /**
     * THE DATABASE (ANDROID)
     * This defines how to grow the "Database Limb" on the Android planet.
     */
    single<MochaDbOld> {
        val androidBuilder = getDatabaseBuilder(ctx = get(), paths = get())

        getRoomDatabase(androidBuilder, dispatcherProvider = get())
    }
    // this is the actual entry point on this specific platform, and its a module that
    // defines how to fetch the database, establishing its own builder that is minimal,
    // but a builder built off of mocha database schema, it then provides its partially
    // appended builder to the common builder, and passes in its own dispatcher, because
    // this is in the same module as the MochaDatabase, this is actually able to just pull
    // that class and we have only a single definition, but in the modular structure we need
    // variations to the schema

    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

    single<BufferProvider> {
        AndroidBufferProvider(
            logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA) }
        )
    }

}