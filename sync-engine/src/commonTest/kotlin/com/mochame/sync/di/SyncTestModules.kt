@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.di

import androidx.sqlite.SQLiteDriver
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.contract.boot.BootStatusUpdater
import com.mochame.contract.di.JanitorMutex
import com.mochame.contract.fixtures.FakeIdentityManager
import com.mochame.contract.fixtures.di.FixturesContractModule
import com.mochame.contract.identity.IdentityManager
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.platform.providers.PlatformContext
import com.mochame.platform.providers.TransactionProvider
import com.mochame.support.TestSupportModule
import com.mochame.sync.SyncConcurrencyModule
import com.mochame.sync.SyncDomainModule
import com.mochame.sync.SyncInfraModule
import com.mochame.sync.SyncOrchestrationModule
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.database.SyncTestDatabase
import com.mochame.sync.domain.providers.SyncUserProvider
import com.mochame.sync.domain.stores.MetadataStoreMaintenance
import com.mochame.sync.domain.stores.MutationLedgerMaintenance
import com.mochame.sync.infrastructure.HlcFactory
import com.mochame.sync.orchestration.SyncJanitor
import com.mochame.system.infra.local.GlobalMetadataDao
import com.mochame.utils.fixtures.di.FakeClockModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
class SyncPersistenceTestModule {
    @Single
    fun provideDatabase(
        context: PlatformContext,
        driver: SQLiteDriver
    ): SyncTestDatabase {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideMetadataDao(db: SyncTestDatabase): SyncMetadataDao =
        db.syncMetadataDao()

    @Single
    fun provideLedgerDao(db: SyncTestDatabase): MutationLedgerDao =
        db.mutationLedgerDao()

    @Single
    fun provideGlobalMetaDao(db: SyncTestDatabase): GlobalMetadataDao =
        db.globalMetaDao()

    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in WrapperPersistence.kt")
}

@KoinApplication(modules = [SyncJanitorTestModule::class])
object JanitorTestApp

@Module(
    includes = [
        SyncOrchestrationModule::class,
        SyncInfraModule::class,
        SyncDomainModule::class,
        SyncConcurrencyModule::class,
        SyncHlcTestModule::class,
        SyncBlobStoreTestModule::class,
        SyncPersistenceTestModule::class,

        TestSupportModule::class,
        FixturesContractModule::class,
        FixturesPlatformModule::class,
    ]
)
@ComponentScan("com.mochame.sync.di")
class SyncJanitorTestModule {
    @Single
    fun provideSyncUserProvider(identityManager: IdentityManager)
    : SyncUserProvider = object : SyncUserProvider {
        override suspend fun getOrCreateNodeId(): String {
            return identityManager.getOrCreateNodeId()
        }
    }
}

@Module(
    includes = [
        FixturesPlatformModule::class,
        FakeClockModule::class,
    ]
)
class SyncBlobStoreTestModule

@Module(
    includes = [
        FakeClockModule::class
    ]
)
class SyncHlcTestModule

//@Module
//class PersistenceTestModule

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
    val manager: FakeIdentityManager,
    @JanitorMutex val janitorMutex: Mutex,
)

//@ExperimentalKermitApi
//@Factory
//data class HLCTestEnvironment(
//    val factory: HlcFactory,
//    val writer: TestLogWriter,
//    val fakeClock: FakeDateTimeUtils
//)

//@ExperimentalKermitApi
//@Factory
//data class SyncPersistenceTestEnv(
//    val executor: ExecutionPolicy,
//    val ledgerDao: MutationLedgerDao,
//    val metadataDao: SyncMetadataDao,
//    val writer: TestLogWriter,
//    val ledgerStore: RealMutationLedger,
//    val metadataStore: RealMetadataStore
//)