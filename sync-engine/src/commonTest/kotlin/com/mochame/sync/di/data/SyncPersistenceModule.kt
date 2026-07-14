package com.mochame.sync.di.data

import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.data.SyncIntentDao
import com.mochame.sync.infrastructure.stores.DefaultSyncIntentStore
import com.mochame.sync.data.SyncMicroSchema
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncPersistenceTestModule::class])
internal object SyncPersistenceTestApp

@Module
@ComponentScan("com.mochame.sync.di.data")
internal class SyncPersistenceTestModule {

    @Single
    fun provideDatabase(): SyncMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideIntentDao(db: SyncMicroSchema): SyncIntentDao = db.syncIntentDao()

    @Single
    fun provideTransactionProvider(): TransactionProvider =
        error("Blueprint Slot Only: Overridden at runtime in WrapperPersistence.kt")

}

@Factory
internal data class PersistenceEnv(
    val intentStore: DefaultSyncIntentStore,
    val intentDao: SyncIntentDao,
)
