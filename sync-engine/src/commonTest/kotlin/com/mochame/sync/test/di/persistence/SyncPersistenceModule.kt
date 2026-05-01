package com.mochame.sync.test.di.persistence

import androidx.sqlite.SQLiteDriver
import com.mochame.platform.providers.PlatformContext
import com.mochame.platform.providers.TransactionProvider
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.test.database.SyncMicroSchema
import com.mochame.system.infra.data.NodeContextDao
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
class SyncPersistenceTestModule {
    @Single
    fun provideDatabase(
        context: PlatformContext,
        driver: SQLiteDriver
    ): SyncMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideMetadataDao(db: SyncMicroSchema): SyncMetadataDao =
        db.syncMetadataDao()

    @Single
    fun provideLedgerDao(db: SyncMicroSchema): MutationLedgerDao =
        db.mutationLedgerDao()

    @Single
    fun provideNodeIdDao(db: SyncMicroSchema): NodeContextDao =
        db.nodeContextDao()

    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in WrapperPersistence.kt")
}


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