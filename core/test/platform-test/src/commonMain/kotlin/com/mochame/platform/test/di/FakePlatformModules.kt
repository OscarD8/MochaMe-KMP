package com.mochame.platform.test.di

import com.mochame.di.CommittedDir
import com.mochame.di.PendingDir
import com.mochame.platform.test.TestWorkspace
import com.mochame.platform.test.createTestWorkspace
import com.mochame.utils.Digest
import com.mochame.utils.Hasher
import kotlinx.io.Source
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.koin.core.annotation.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/**
 * Provides a fake Hasher and test file workspace.
 */
@Module
class FakePlatformModule {
    val definitions = module {
        factory<Hasher> {
            Hasher {
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
        }

        single { SystemFileSystem }

        single { createTestWorkspace() }

        factory(qualifier<PendingDir>()) { get<TestWorkspace>().pending }
        factory(qualifier<CommittedDir>()) { get<TestWorkspace>().committed }
    }
}