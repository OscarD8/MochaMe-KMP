package com.mochame.platform.di

import com.mochame.contract.di.CommittedDir
import com.mochame.contract.di.PendingDir
import com.mochame.logger.LogTags
import com.mochame.platform.providers.AppPathsProvider
import com.mochame.contract.providers.BufferProvider
import com.mochame.platform.providers.DatabaseLocation
import com.mochame.platform.providers.JvmBufferProvider
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

@Module
actual class InternalPlatformModule : KoinComponent {

    @Single
    fun provideFileSystem(): FileSystem = SystemFileSystem

    @Single
    fun provideAppPaths(): AppPathsProvider {
        val userHome = System.getProperty("user.home")
        val baseDir = "$userHome/.mochame"

        return object : AppPathsProvider {
            override val blobPending = "$baseDir/blobs/pending"
            override val blobCommitted = "$baseDir/blobs/committed"
            override val databasePath = "$baseDir/mocha_me.db"
        }
    }

    @Single(binds = [DatabaseLocation::class])
    fun provideDatabasePath(path: AppPathsProvider): DatabaseLocation =
        DatabaseLocation.OnDisk(path.databasePath)

    @Single
    @PendingDir
    fun providePendingPath(paths: AppPathsProvider): Path = Path(paths.blobPending)

    @Single
    @CommittedDir
    fun provideCommittedPath(paths: AppPathsProvider): Path = Path(paths.blobCommitted)

    @Single
    fun provideBufferProvider(): BufferProvider {
        return JvmBufferProvider(
            logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA) }
        )
    }
}