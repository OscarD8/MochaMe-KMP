package com.mochame.platform.di

import android.content.Context
import com.mochame.annotations.CommittedDir
import com.mochame.annotations.PendingDir
import com.mochame.platform.providers.AppPathsProvider
import com.mochame.platform.providers.DatabaseLocation
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
actual class InternalPlatformModule {

    @Single
    fun provideFileSystem(): FileSystem = SystemFileSystem

    @Single
    fun provideAppPaths(context: Context): AppPathsProvider {
        val baseDir = context.filesDir.absolutePath
        return object : AppPathsProvider {
            override val blobPending = "$baseDir/blobs/pending"
            override val blobCommitted = "$baseDir/blobs/committed"
            override val databasePath = "$baseDir/mocha.db"
        }
    }

    @Single(binds = [DatabaseLocation::class])
    fun provideDatabasePath(
        path: AppPathsProvider
    ): DatabaseLocation = DatabaseLocation.OnDisk(path.databasePath)

    @Single
    @PendingDir
    fun providePendingPath(paths: AppPathsProvider): Path = Path(paths.blobPending)

    @Single
    @CommittedDir
    fun provideCommittedPath(paths: AppPathsProvider): Path = Path(paths.blobCommitted)
}