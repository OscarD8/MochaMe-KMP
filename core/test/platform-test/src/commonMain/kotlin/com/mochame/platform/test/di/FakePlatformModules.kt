package com.mochame.platform.test.di

import co.touchlab.kermit.Logger
import com.mochame.di.CommittedDir
import com.mochame.di.PendingDir
import com.mochame.platform.policies.ExecutionPolicy
import com.mochame.platform.policies.SqliteResiliencePolicy
import com.mochame.platform.test.TestWorkspace
import com.mochame.platform.test.createTestWorkspace
import com.mochame.support.di.TestLoggerModule
import com.mochame.utils.Digest
import com.mochame.utils.Hasher
import kotlinx.io.Source
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/**
 * Provides a fake Hasher and test file workspace.
 */
@Module(
    includes = [TestLoggerModule::class]
)
class FakePlatformModule {

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