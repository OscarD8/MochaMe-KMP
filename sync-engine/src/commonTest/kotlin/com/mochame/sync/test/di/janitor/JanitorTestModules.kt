package com.mochame.sync.test.di.janitor

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.sync.api.boot.BootStatusUpdater
import com.mochame.contract.di.JanitorMutex
import com.mochame.contract.fixtures.FakeNodeContextManager
import com.mochame.contract.fixtures.di.FixturesContractModule
import com.mochame.sync.api.node.NodeContextManager
import com.mochame.contract.providers.TransactionProvider
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.SyncConcurrencyModule
import com.mochame.sync.SyncDomainModule
import com.mochame.sync.SyncInfraModule
import com.mochame.sync.SyncOrchestrationModule
import com.mochame.sync.data.daos.FeatureSyncStateDao
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.sync.domain.stores.FeatureSyncStateMaintenanceStore
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.orchestration.SyncJanitor
import com.mochame.sync.test.di.blob.SyncBlobStoreTestModule
import com.mochame.sync.test.di.hlc.FakeHlcFactoryModule
import com.mochame.sync.test.di.hlc.SyncHlcUnitTestModule
import com.mochame.sync.test.di.data.SyncPersistenceTestModule
import com.mochame.sync.test.fakes.FakeHlcFactory
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@KoinApplication(modules = [SyncJanitorTestModule::class])
internal object JanitorTestApp


@Module(
    includes = [
        TestSupportModule::class,
        FixturesContractModule::class,
        FixturesPlatformModule::class,
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
@ComponentScan("com.mochame.sync.test.di.janitor")
internal class SyncJanitorTestModule {
    @Single
    fun provideSyncUserProvider(nodeContextManager: NodeContextManager)
            : SyncUserProvider = object : SyncUserProvider {
        override suspend fun getOrCreateNodeId(): String {
            return nodeContextManager.getOrCreateNodeId()
        }
    }
}


@Factory
@ExperimentalKermitApi
internal data class JanitorTestEnv(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val bootUpdater: BootStatusUpdater,
    val hlcFactory: FakeHlcFactory,
    val nodeSyncStateStore: FeatureSyncStateMaintenanceStore,
    val intentStore: SyncIntentMaintenanceStore,
    val transactor: TransactionProvider,
    val metadataDao: FeatureSyncStateDao,
    val manager: FakeNodeContextManager,
    @JanitorMutex val janitorMutex: Mutex,
)