package com.mochame.sync

import com.mochame.di.BlobMutex
import com.mochame.di.JanitorMutex
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        SyncDomainModule::class,
        SyncConcurrencyModule::class,
        SyncInfraModule::class,
        SyncOrchestrationModule::class
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
@ComponentScan("com.mochame.sync.domain")
class SyncDomainModule

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
}
