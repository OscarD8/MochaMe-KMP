package com.mochame.sync.di.blob

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.platform.fixtures.FakeDigestFactory
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.infrastructure.stores.DefaultBlobStore
import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [SyncBlobStoreTestModule::class])
internal class BlobStoreTestApp

@Module(
    includes = [
        FakeTimeProviderModule::class,
        FixturesPlatformModule::class,
    ]
)
@ComponentScan("com.mochame.sync.di.blob")
internal class SyncBlobStoreTestModule

@ExperimentalKermitApi
@Factory
internal class BlobStoreTestEnv(
    val store: DefaultBlobStore,
    val digestFactory: FakeDigestFactory,
    val writer: TestLogWriter,
    val clock: FakeTimeProvider
)


