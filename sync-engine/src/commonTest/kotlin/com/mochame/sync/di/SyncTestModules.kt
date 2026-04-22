@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.di.IdentityMutex
import com.mochame.di.JanitorMutex
import com.mochame.orchestrator.BootStatusUpdater
import com.mochame.orchestrator.GlobalMetadataStore
import com.mochame.orchestrator.test.di.OrchestratorTestModule
import com.mochame.platform.global.GlobalMetadataDao
import com.mochame.platform.policies.ExecutionPolicy
import com.mochame.platform.providers.TransactionProvider
import com.mochame.platform.test.di.FakePlatformModule
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.database.SyncTestDatabase
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.sync.domain.stores.MetadataStoreMaintenance
import com.mochame.sync.domain.stores.MutationLedgerMaintenance
import com.mochame.sync.infrastructure.HlcFactory
import com.mochame.sync.infrastructure.stores.RealMutationLedger
import com.mochame.sync.infrastructure.stores.RealMetadataStore
import com.mochame.sync.orchestration.SyncJanitor
import come.mochame.utils.test.FakeDateTimeUtils
import come.mochame.utils.test.di.FakeClockModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


@Module(
    includes = [
        SyncEngineModule::class,
        HlcTestModule::class,
        OrchestratorTestModule::class,
        FakePlatformModule::class,
        BlobStoreTestModule::class,
        FakeClockModule::class
    ]
)
class JanitorTestModule {
    val definitions = module {
        factoryOf(::JanitorTestEnvironment)
    }
}

@Module(
    includes = [
        SyncEngineModule::class,
        FakePlatformModule::class,
        FakeClockModule::class
    ]
)
class BlobStoreTestModule {
    val definitions = module {
        includes(//future test env
        )
    }

}

@Module(
    includes = [
        FakeClockModule::class
    ]
)
class HlcTestModule {
    val definitions = module {
        singleOf(::HLCTestEnvironment)
    }
}

@Module
class PersistenceTestModule {
    val definitions = module {
        singleOf(::SyncPersistenceTestEnv)
    }
}


@ExperimentalKermitApi
data class JanitorTestEnvironment(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val bootUpdater: BootStatusUpdater,
    val hlcFactory: HlcFactory,
    val metadataStore: MetadataStoreMaintenance,
    val ledgerMaintenance: MutationLedgerMaintenance,
    val transactor: TransactionProvider,
    val metadataDao: SyncMetadataDao,
    val manager: SyncUserProvider,
    @JanitorMutex val janitorMutex: Mutex,
    @IdentityMutex val identityMutex: Mutex,
)

@ExperimentalKermitApi
data class HLCTestEnvironment(
    val factory: HlcFactory,
    val writer: TestLogWriter,
    val fakeClock: FakeDateTimeUtils
)

@ExperimentalKermitApi
data class IdentityTestEnvironment(
    val manager: SyncUserProvider,
    val globalMetaStore: GlobalMetadataStore,
    val globalMetadataDao: GlobalMetadataDao,
    val db: SyncTestDatabase,
    val writer: TestLogWriter
)

@ExperimentalKermitApi
data class SyncPersistenceTestEnv(
    val executor: ExecutionPolicy,
    val ledgerDao: MutationLedgerDao,
    val metadataDao: SyncMetadataDao,
    val writer: TestLogWriter,
    val db: SyncTestDatabase,
    val ledgerStore: RealMutationLedger,
    val metadataStore: RealMetadataStore
)