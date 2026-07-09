package com.mochame.sync.test.di.persistence

import androidx.sqlite.SQLiteDriver
import com.mochame.contract.providers.TransactionProvider
import com.mochame.platform.providers.PlatformContext
import com.mochame.sync.data.daos.SyncIntentDao
import com.mochame.sync.data.daos.SyncModuleStateDao
import com.mochame.sync.test.schema.SyncMicroSchema
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncPersistenceTestModule::class])
internal object SyncPersistenceTestApp

@Module
internal class SyncPersistenceTestModule {
    @Single
    fun provideDatabase(
        context: PlatformContext,
        driver: SQLiteDriver
    ): SyncMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideModuleStateDao(db: SyncMicroSchema): SyncModuleStateDao =
        db.syncModuleStateDao()

    @Single
    fun provideIntentDao(db: SyncMicroSchema): SyncIntentDao =
        db.syncIntentDao()

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