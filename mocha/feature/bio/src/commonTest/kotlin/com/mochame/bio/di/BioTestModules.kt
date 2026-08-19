package com.mochame.bio.di

import com.mochame.bio.data.BioDao
import com.mochame.bio.data.BioMicroSchema
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.fixtures.di.FixturesSyncModule
import com.mochame.utils.fixtures.di.FakeTimeProviderModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [BioInfraTestModule::class])
internal object BioDaoTestApp

@Module(
    includes = [
        BioProductionModule::class,
        TestSupportModule::class,
        FixturesSyncModule::class,
        FixturesPlatformModule::class,
        FakeTimeProviderModule::class
    ]
)
class BioInfraTestModule {
    @Single
    fun provideBioSchema(): BioMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }

    @Single
    fun provideBioDao(db: BioMicroSchema): BioDao = db.bioDao()
}

