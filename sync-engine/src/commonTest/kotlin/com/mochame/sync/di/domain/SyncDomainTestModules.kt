package com.mochame.sync.di.domain

import com.mochame.sync.di.FakeSyncStoresModule
import com.mochame.sync.domain.usecase.PruneOldEntriesUseCase
import com.mochame.sync.fakes.FakeSyncIntentStore
import com.mochame.utils.fixtures.FakeDateTimeUtils
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [SyncPruneUseCaseModule::class])
internal class PruneEntriesUseCaseTestApp

@Module(includes = [FakeSyncStoresModule::class])
@ComponentScan("com.mochame.sync.di.domain")
internal class SyncPruneUseCaseModule

@Factory
internal data class PruneEntriesTestEnv(
    val useCase: PruneOldEntriesUseCase,
    val fakeIntentStore: FakeSyncIntentStore,
    val fakeDateTimeUtils: FakeDateTimeUtils
)