package com.mochame.bio.di

import com.mochame.bio.data.BioDao
import com.mochame.bio.data.BioMicroSchema
import com.mochame.sync.fixtures.di.FixturesSyncModule
import com.mochame.utils.fixtures.di.FakeMochaTimeProviderModule
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [BioInfraTestModule::class])
internal object BioDaoTestApp

@Module(
    includes = [
        BioProductionModule::class,
        FixturesSyncModule::class,
        FakeMochaTimeProviderModule::class
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

