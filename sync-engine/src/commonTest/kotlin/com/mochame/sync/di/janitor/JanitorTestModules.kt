package com.mochame.sync.di.janitor

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.annotations.JanitorMutex
import com.mochame.node.fixtures.FakeBootStatusManager
import com.mochame.node.fixtures.FakeExecutionPolicy
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.FakeTransactionProvider
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.di.FakeSyncStoresModule
import com.mochame.sync.di.SyncConcurrencyModule
import com.mochame.sync.di.SyncDomainModule
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.di.SyncOrchestrationModule
import com.mochame.sync.di.domain.SyncPruneUseCaseTestModule
import com.mochame.sync.di.hlc.FakeHlcFactoryModule
import com.mochame.sync.fakes.FakeHlcFactory
import com.mochame.sync.fakes.FakeSyncIntentStore
import com.mochame.sync.infrastructure.stores.DefaultBlobStore
import com.mochame.sync.orchestration.SyncJanitor
import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module


@KoinApplication(modules = [SyncJanitorTestModule::class])
internal object JanitorTestApp


@Module(
    includes = [
        TestSupportModule::class,
        FixturesNodeModule::class,
        FixturesPlatformModule::class,

        SyncOrchestrationModule::class,
        SyncDomainModule::class,
        SyncInfraModule::class,
        SyncConcurrencyModule::class,
        SyncPruneUseCaseTestModule::class,

        FakeHlcFactoryModule::class,
        FakeSyncStoresModule::class,
        FakeTimeProviderModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.janitor")
internal class SyncJanitorTestModule

@Factory
@ExperimentalKermitApi
internal data class JanitorTestEnv(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val fakeClock: FakeTimeProvider,
    val bootUpdater: FakeBootStatusManager,
    val hlcFactory: FakeHlcFactory,
    val nodeManager: FakeNodeContextManager,
    val blobStore: DefaultBlobStore,
    val intentStore: FakeSyncIntentStore,
    val transactor: FakeTransactionProvider,
    val executor: FakeExecutionPolicy,
    @JanitorMutex val janitorMutex: Mutex,
)