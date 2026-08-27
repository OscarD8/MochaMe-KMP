package com.mochame.bio.di

import com.mochame.bio.data.BioMicroSchema
import com.mochame.bio.data.DailyContextDao
import com.mochame.bio.infrastructure.DefaultDailyContextRepository
import com.mochame.node.fixtures.SpyBootStatusManager
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.fixtures.di.FixturesSyncModule
import com.mochame.utils.fixtures.MochaFakeTimeUtils
import com.mochame.utils.fixtures.di.FakeMochaTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Instant

@KoinApplication(modules = [BioInfraTestModule::class])
internal object BioInfraTestApp

@Module(
    includes = [
        BioProductionModule::class,
        FixturesSyncModule::class,
        FakeMochaTimeProviderModule::class
    ]
)
@ComponentScan("com.mochame.bio.di")
internal class BioInfraTestModule {
    @Single
    fun provideBioSchema(): BioMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideDailyContextDao(db: BioMicroSchema): DailyContextDao = db.bioDao()
}

@Factory
internal class BioTestEnv(
    val contextRepo: DefaultDailyContextRepository,
    val contextDao: DailyContextDao,
    val fakeClock: MochaFakeTimeUtils,
    val intentStore: FakeSyncIntentStore,
    val bootProvider: SpyBootStatusManager
)
