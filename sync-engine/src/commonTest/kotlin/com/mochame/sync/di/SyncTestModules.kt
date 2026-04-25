@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.di

import androidx.sqlite.SQLiteDriver
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.di.IdentityMutex
import com.mochame.di.JanitorMutex
import com.mochame.metadata.BootStatusUpdater
import com.mochame.metadata.test.di.OrchestratorTestModule
import com.mochame.orchestrator.IdentityManager
import com.mochame.platform.global.GlobalMetadataDao
import com.mochame.platform.providers.PlatformContext
import com.mochame.platform.providers.TransactionProvider
import com.mochame.platform.test.di.FakePlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.support.di.TestLoggerModule
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
import com.mochame.utils.test.di.FakeClockModule
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

    // -----------------------------------------------------------
    // DAO BLUEPRINT SLOTS
    // -----------------------------------------------------------
    // These satisfy the compiler's audit for the Environment factories
    @Single
    fun provideMetadataDao(db: SyncTestDatabase): SyncMetadataDao =
        db.syncMetadataDao()

    @Single
    fun provideLedgerDao(db: SyncTestDatabase): MutationLedgerDao =
        db.mutationLedgerDao()

    @Single
    fun provideGlobalMetaDao(db: SyncTestDatabase): GlobalMetadataDao =
        db.globalMetaDao()

    // -----------------------------------------------------------
    // TRANSACTION PROVIDER SLOT
    // -----------------------------------------------------------
    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in TestWrappers.kt")
}

@KoinApplication(modules = [JanitorTestModule::class])
object JanitorTestApp

@Module(
    includes = [
        SyncOrchestrationModule::class,
        SyncInfraModule::class,
        SyncDomainModule::class,
        SyncConcurrencyModule::class,
        HlcTestModule::class,
        OrchestratorTestModule::class,
        BlobStoreTestModule::class,
        TestSupportModule::class,
        SyncPersistenceTestModule::class,
        TestLoggerModule::class,
        FakePlatformModule::class,
    ]
)
@ComponentScan("com.mochame.sync.di")
class JanitorTestModule {

    @Single
    fun provideSyncUserProvider(
        identityManager: IdentityManager
    ): SyncUserProvider = object : SyncUserProvider {
        override suspend fun getOrCreateNodeId(): String {
            return identityManager.getOrCreateNodeId()
        }
    }
}

@Module(
    includes = [
        FakePlatformModule::class,
        FakeClockModule::class,
        TestLoggerModule::class
    ]
)
class BlobStoreTestModule

@Module(
    includes = [
        FakeClockModule::class
    ]
)
class HlcTestModule

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
    val manager: SyncUserProvider,
    @JanitorMutex val janitorMutex: Mutex,
    @IdentityMutex val identityMutex: Mutex,
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