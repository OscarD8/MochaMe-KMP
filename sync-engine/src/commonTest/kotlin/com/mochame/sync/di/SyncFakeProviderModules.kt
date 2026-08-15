package com.mochame.sync.di

import com.mochame.sync.di.blob.SyncBlobStoreTestModule
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.fixtures.FakeSyncWorkerHook
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [SyncBlobStoreTestModule::class])
class FakeSyncStoresModule {
    @Single(binds = [SyncIntentMaintenanceStore::class, SyncIntentStore::class])
    fun provideFakeSyncIntentStore(): FakeSyncIntentStore = FakeSyncIntentStore()
}

@Module
class FakeSyncWorkerHookModule {
    @Single(binds = [SyncWorkerHook::class])
    fun provideFakeSyncWorkerHook(): FakeSyncWorkerHook = FakeSyncWorkerHook()
}