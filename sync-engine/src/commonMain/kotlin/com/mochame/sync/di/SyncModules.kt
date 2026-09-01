package com.mochame.sync.di

import com.mochame.annotations.BlobMutex
import com.mochame.annotations.CoordinatorMutex
import com.mochame.annotations.JanitorMutex
import com.mochame.logger.LoggerModule
import com.mochame.sync.domain.model.SyncConfig
import com.mochame.utils.di.UtilsModule
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        LoggerModule::class,
        UtilsModule::class,

        SyncDataModule::class,
        SyncDomainModule::class,
        SyncConcurrencyModule::class,
        SyncInfraModule::class,
        SyncOrchestrationModule::class,
        SyncStoresModule::class,
        SyncConfigModule::class
    ]
)
class SyncProductionModule

@Module
@ComponentScan("com.mochame.sync.data")
class SyncDataModule

@Module
@ComponentScan("com.mochame.sync.infrastructure")
class SyncInfraModule

@Module
@ComponentScan("com.mochame.sync.infrastructure.stores")
class SyncStoresModule

@Module
@ComponentScan("com.mochame.sync.domain")
class SyncDomainModule

@Module
class SyncConfigModule {
    @Single
    fun provideSyncConfig(): SyncConfig = SyncConfig()
}

@Module
@ComponentScan("com.mochame.sync.orchestration")
class SyncOrchestrationModule


@Module
class SyncConcurrencyModule {
    @Single
    @JanitorMutex
    fun provideJanitorMutex(): Mutex = Mutex()

    @Single
    @BlobMutex
    fun provideBlobMutex(): Mutex = Mutex()

    @Single
    @CoordinatorMutex
    fun provideCoordinatorMutex(): Mutex = Mutex()
}
