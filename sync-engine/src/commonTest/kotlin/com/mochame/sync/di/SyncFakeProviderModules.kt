package com.mochame.sync.di

import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.fakes.FakeSyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class FakeSyncStoresModule {
    @Single(binds = [SyncIntentMaintenanceStore::class, SyncIntentStore::class])
    fun provideFakeSyncIntentStore(): FakeSyncIntentStore = FakeSyncIntentStore()
}