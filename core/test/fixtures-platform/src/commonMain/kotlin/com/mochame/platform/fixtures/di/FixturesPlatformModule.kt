package com.mochame.platform.fixtures.di

import co.touchlab.kermit.Logger
import com.mochame.contract.di.CommittedDir
import com.mochame.contract.di.PendingDir
import com.mochame.platform.providers.Digest
import com.mochame.platform.providers.Hasher
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.policies.SqliteResiliencePolicy
import com.mochame.platform.fixtures.TestWorkspace
import com.mochame.platform.fixtures.createTestWorkspace
import com.mochame.sync.spi.policy.ExecutionPolicy
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Provides a fake Hasher and test file workspace.
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

            override fun digest(): ByteArray = bytes.toByteArray()
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

    @Single
    fun provideExecutionPolicy(logger: Logger): ExecutionPolicy =
        SqliteResiliencePolicy(logger)
}