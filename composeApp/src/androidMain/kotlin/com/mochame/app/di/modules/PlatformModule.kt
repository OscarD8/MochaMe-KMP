package com.mochame.app.di.modules

import android.content.Context
import com.mochame.app.database.getDatabaseBuilder // The Android-only path finder
import com.mochame.app.data.local.room.getRoomDatabase    // The Shared configuration
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.di.providers.AppPaths
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.infrastructure.utils.AndroidBufferProvider
import com.mochame.app.infrastructure.utils.BufferProvider
import com.mochame.app.infrastructure.utils.Hasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
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
    single<MochaDatabase> {
        val androidBuilder = getDatabaseBuilder(ctx = get(), paths = get())

        getRoomDatabase(androidBuilder, dispatcherProvider = get())
    }

    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

    single<BufferProvider> { AndroidBufferProvider() }

}