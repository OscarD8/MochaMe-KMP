@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.di.domain

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.sync.di.FakeSyncStoresModule
import com.mochame.sync.domain.TEST_PRUNE_DAYS
import com.mochame.sync.domain.usecase.PruneIntentsUseCase
import com.mochame.sync.fakes.FakeSyncIntentStore
import com.mochame.utils.fixtures.FakeTimeProvider
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [SyncPruneIntentsTestModule::class])
internal class PruneIntentsUseCaseTestApp

@Module(
    includes = [
        FakeSyncStoresModule::class,
        TestLogWriter::class,
        FakeTimeProviderModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.domain")
internal class SyncPruneIntentsTestModule {
    @Single
    fun provideUseCase(
        intentStore: FakeSyncIntentStore,
        dateTimeUtils: FakeTimeProvider,
        logger: Logger
    ): PruneIntentsUseCase =
        PruneIntentsUseCase(intentStore, dateTimeUtils, TEST_PRUNE_DAYS, 2, logger)
}

@Factory
internal data class PruneIntentsTestEnv(
    val useCase: PruneIntentsUseCase,
    val fakeStore: FakeSyncIntentStore,
    val fakeClock: FakeTimeProvider,
    val logWriter: TestLogWriter,
    val fakeDateTimeUtils: FakeTimeProvider,
)