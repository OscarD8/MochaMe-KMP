package com.mochame.sync.fixtures.di

import co.touchlab.kermit.Logger
import com.mochame.annotations.IoContext
import com.mochame.logger.test.TestLoggerModule
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.FakeDigestFactory
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.fixtures.FakeBlobStore
import com.mochame.sync.fixtures.FakeKeyedLocker
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.fixtures.FakeSyncWorkerHook
import com.mochame.sync.fixtures.hlc.FakeHlcFactory
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.BlobStore
import com.mochame.sync.spi.infrastructure.KeyedLocker
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import com.mochame.utils.fixtures.FakeTimeUtils
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Module(
    includes = [
        FakeTimeProviderModule::class,
        FixturesPlatformModule::class,
        FixturesNodeModule::class,
        TestSupportModule::class,
        TestLoggerModule::class,
    ]
)
class FixturesSyncModule {

    @Single(binds = [BlobStore::class, FakeBlobStore::class])
    fun provideFakeBlobStore(digestFactory: FakeDigestFactory): FakeBlobStore =
        FakeBlobStore(digestFactory)

    @Single(binds = [SyncIntentStore::class, SyncIntentMaintenanceStore::class, FakeSyncIntentStore::class])
    fun provideFakeSyncIntentStore(): FakeSyncIntentStore =
        FakeSyncIntentStore()

    @Single(binds = [SyncWorkerHook::class, FakeSyncWorkerHook::class])
    fun provideFakeWorkerHook(): FakeSyncWorkerHook =
        FakeSyncWorkerHook()

    @Single(binds = [HlcFactory::class, FakeHlcFactory::class])
    fun provideFakeHlcFactory(timeProvider: FakeTimeUtils): FakeHlcFactory =
        FakeHlcFactory(timeProvider)

    @Single(binds = [KeyedLocker::class, FakeKeyedLocker::class])
    fun provideFakeKeyedLocker(): FakeKeyedLocker =
        FakeKeyedLocker()

    @Single
    fun provideFakeLocalFirstDependencies(
        hlcFactory: HlcFactory,
        transactor: TransactionProvider,
        blobStore: BlobStore,
        intentStore: SyncIntentStore,
        workerHook: SyncWorkerHook,
        executor: ExecutionPolicy,
        locker: KeyedLocker,
        logger: Logger,
        nodeManager: NodeContextManager,
        bootStatus: BootStatusProvider,
        @IoContext ioContext: CoroutineContext
    ): LocalFirstDependencies = LocalFirstDependencies(
        hlcFactory = hlcFactory,
        transactor = transactor,
        blobStore = blobStore,
        intentStore = intentStore,
        workerHook = workerHook,
        executor = executor,
        locker = locker,
        logger = logger,
        nodeManager = nodeManager,
        bootProvider = bootStatus,
        ioContext = ioContext
    )
}