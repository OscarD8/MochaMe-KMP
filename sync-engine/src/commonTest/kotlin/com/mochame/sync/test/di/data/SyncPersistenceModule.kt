package com.mochame.sync.test.di.data

import androidx.sqlite.SQLiteDriver
import com.mochame.contract.providers.TransactionProvider
import com.mochame.platform.providers.PlatformContext
import com.mochame.sync.api.stores.FeatureSyncStateStore
import com.mochame.sync.data.daos.SyncIntentDao
import com.mochame.sync.data.daos.FeatureSyncStateDao
import com.mochame.sync.infrastructure.stores.DefaultSyncIntentStore
import com.mochame.sync.test.schema.SyncMicroSchema
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncPersistenceTestModule::class])
internal object SyncPersistenceTestApp

@Module
@ComponentScan("com.mochame.sync.test.di.data")
internal class SyncPersistenceTestModule {
    @Single
    fun provideDatabase(
        context: PlatformContext,
        driver: SQLiteDriver
    ): SyncMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideFeatureSyncStateDao(db: SyncMicroSchema): FeatureSyncStateDao =
        db.featureSyncStateDao()

    @Single
    fun provideIntentDao(db: SyncMicroSchema): SyncIntentDao = db.syncIntentDao()

    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in WrapperPersistence.kt")

}

@Factory
internal data class PersistenceEnv(
    val intentStore: DefaultSyncIntentStore, // concrete class
    val moduleStore: FeatureSyncStateStore,
    val intentDao: SyncIntentDao,
    val moduleDao: FeatureSyncStateDao
)
