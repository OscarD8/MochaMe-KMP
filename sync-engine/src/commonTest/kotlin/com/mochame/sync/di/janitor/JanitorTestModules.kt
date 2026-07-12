package com.mochame.sync.di.janitor

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.di.JanitorMutex
import com.mochame.contract.providers.TransactionProvider
import com.mochame.node.fixtures.FakeBootStatusManager
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.di.SyncConcurrencyModule
import com.mochame.sync.di.SyncDomainModule
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.di.SyncOrchestrationModule
import com.mochame.sync.di.blob.SyncBlobStoreTestModule
import com.mochame.sync.di.data.SyncPersistenceTestModule
import com.mochame.sync.di.hlc.FakeHlcFactoryModule
import com.mochame.sync.di.hlc.SyncHlcUnitTestModule
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.fakes.FakeHlcFactory
import com.mochame.sync.orchestration.SyncJanitor
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
        FixturesPlatformModule::class,
        FixturesNodeModule::class,
        FakeHlcFactoryModule::class,

        SyncOrchestrationModule::class,
        SyncDomainModule::class,
        SyncInfraModule::class,
        SyncConcurrencyModule::class,
        SyncBlobStoreTestModule::class,
        SyncPersistenceTestModule::class,
        SyncHlcUnitTestModule::class,
    ]
)
@ComponentScan("com.mochame.sync.di.janitor")
internal class SyncJanitorTestModule

@Factory
@ExperimentalKermitApi
internal data class JanitorTestEnv(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val bootUpdater: FakeBootStatusManager,
    val hlcFactory: FakeHlcFactory,
    val nodeManager: FakeNodeContextManager,
    val intentStore: SyncIntentMaintenanceStore,
    val transactor: TransactionProvider,
    @JanitorMutex val janitorMutex: Mutex,
)