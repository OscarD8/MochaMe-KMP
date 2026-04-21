package com.mochame.sync.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.core.di.AppScope
import com.mochame.core.di.IdentityMutex
import com.mochame.core.di.JanitorMutex
import com.mochame.core.policies.ExecutionPolicy
import com.mochame.metadata.GlobalMetadataDao
import com.mochame.sync.infrastructure.stores.RoomMetadataStore
import com.mochame.sync.infrastructure.stores.RealMutationLedger
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.database.SyncTestDatabase
import com.mochame.core.providers.TransactionProvider
import com.mochame.sync.domain.stores.MetadataStoreMaintenance
import com.mochame.sync.domain.stores.MutationLedgerMaintenance
import com.mochame.sync.infrastructure.HlcFactory
import com.mochame.metadata.IdentityManager
import com.mochame.platform.test.FakeBlobStore
import com.mochame.support.di.fakeDateTimeUtilsModule
import com.mochame.sync.domain.stores.BlobReader
import com.mochame.sync.domain.stores.BlobStager
import com.mochame.sync.domain.BootStatusUpdater
import com.mochame.sync.orchestration.SyncJanitor
import com.mochame.support.fakes.FakeDateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Factory
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

object SyncTestModules {

    val fakeBlobStoreModule = module {
        single<FakeBlobStore> { FakeBlobStore() }.binds(
            arrayOf(
                BlobReader::class,
                BlobStager::class
            )
        )
    }

    @OptIn(ExperimentalKermitApi::class)
    val janitorTestEnvironmentModule = module {
        includes(
            fakeDateTimeUtilsModule,
            fakeBlobStoreModule
        )

        factoryOf(::JanitorTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val hlcTestEnvironmentModule = module {
        includes(
            fakeDateTimeUtilsModule,
        )
        single<SyncMetadataDao> { get<MochaDbOld>().syncMetadataDao() }
        singleOf(::HLCTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val identityTestEnvironmentModule = module {
        singleOf(::IdentityTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val syncPersistenceTestModule = module {
        singleOf(::SyncPersistenceTestEnv)
    }

}


@Factory
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
    val manager: IdentityManager,
    @JanitorMutex val janitorMutex: Mutex,
    @IdentityMutex val identityMutex: Mutex,
    @AppScope val appScope: CoroutineScope
)

@ExperimentalKermitApi
data class HLCTestEnvironment(
    val factory: HlcFactory,
    val writer: TestLogWriter,
    val fakeClock: FakeDateTimeUtils
)

@ExperimentalKermitApi
data class IdentityTestEnvironment(
    val manager: IdentityManager,
    val globalMetaStore: RoomGlobalMetadataStore,
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
    val metadataStore: RoomMetadataStore
)