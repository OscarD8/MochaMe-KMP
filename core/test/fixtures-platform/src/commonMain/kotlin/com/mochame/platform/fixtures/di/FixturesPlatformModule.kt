package com.mochame.platform.fixtures.di

import com.mochame.annotations.CommittedDir
import com.mochame.annotations.PendingDir
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.FakeTransactionProvider
import com.mochame.platform.fixtures.TestWorkspace
import com.mochame.platform.fixtures.createTestWorkspace
import com.mochame.sync.spi.infrastructure.Digest
import com.mochame.sync.spi.infrastructure.Hasher
import com.mochame.sync.spi.infrastructure.TransactionProvider
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Provides a fake platform integration module with no SQLite usage, for Unit testing.
 */
@Module([TestLoggerModule::class])
class FixturesPlatformModule {

    @Factory
    fun provideHasher(): Hasher = Hasher {
        object : Digest {
            private val buffer = mutableListOf<Byte>()
            private val bytes = mutableListOf<Byte>()

            override fun update(source: Source) {
                val snapshot = source.peek().readByteArray()
                buffer.addAll(snapshot.toList())
            }

            override fun digest(): ByteArray = buffer.toByteArray()
        }
    }

    @Single
    fun provideFileSystem(): FileSystem = SystemFileSystem

    @Single
    fun provideTestWorkspace(): TestWorkspace = createTestWorkspace()

    @Factory
    @PendingDir
    fun providePendingDir(workspace: TestWorkspace): Path = workspace.pending

    @Factory
    @CommittedDir
    fun provideCommittedDir(workspace: TestWorkspace): Path = workspace.committed

    @Single(binds = [TransactionProvider::class, FakeTransactionProvider::class])
    fun provideFakeTransactionProvider() : TransactionProvider =
        FakeTransactionProvider()

}

