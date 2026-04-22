package com.mochame.androidapp.di

import android.content.Context
import com.mochame.platform.di.DefaultContext
import com.mochame.platform.di.IoContext
import com.mochame.platform.di.MainContext
import com.mochame.platform.providers.AppPathsProvider
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


    @Single
    class AndroidAppPathsProvider(private val context: Context) : AppPathsProvider {
        val baseDir: String = context.filesDir.absolutePath
        override val blobPending = "$baseDir/blobs/pending"
        override val blobCommitted = "$baseDir/blobs/committed"
        override val databasePath = "$baseDir/mocha.db"
    }

    @Module
    class AndroidPlatformModule {

        @Single
        @IoContext
        fun provideIoContext(): CoroutineContext = Dispatchers.IO

        @Single
        @MainContext
        fun provideMainContext(): CoroutineContext = Dispatchers.Main

        @Single
        @DefaultContext
        fun provideDefaultContext(): CoroutineContext = Dispatchers.Default
    }

    @Module
    @ComponentScan
    class AndroidDatabaseProvider {
    }
