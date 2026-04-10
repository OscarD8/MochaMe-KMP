package com.mochame.app.di.modules

import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.getDatabaseBuilder
import com.mochame.app.data.local.room.getRoomDatabase
import com.mochame.app.di.providers.AppPaths
import com.mochame.app.di.providers.DispatcherProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import platform.posix.getenv


@OptIn(ExperimentalForeignApi::class)
actual val platformModule = module {

    single<DispatcherProvider> {
        object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val unconfined = Dispatchers.Unconfined
        }
    }

    single<MochaDatabase> {
        // We get the builder from JVM, then finish it in COMMON
        getRoomDatabase(
            getDatabaseBuilder(paths = get()),
            dispatcherProvider = get()
        )
    }

    single<AppPaths> {
        // 1. Query the OS environment for the "HOME" variable
        // getenv returns a C-pointer (ByteVar). .toKString() converts it to a Kotlin String.
        val userHome = getenv("HOME")?.toKString() ?: "/tmp"

        // 2. Define the hidden local-first directory
        val baseDir = "$userHome/.mochame"

        // 3. Construct the Bit-Stable paths
        AppPaths(
            blobPending = "$baseDir/blobs/pending",
            blobCommitted = "$baseDir/blobs/committed",
            databasePath = "$baseDir/mocha_me.db"
        )
    }

}



//    single<FileSystem> { SystemFileSystem }


