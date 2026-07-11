package com.mochame.bio.di

import com.mochame.bio.data.BioDao
import com.mochame.bio.database.BioMicroSchema
import com.mochame.support.TestSupportModule
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication(modules = [BioDaoTestModule::class])
class BioDaoTestApp

@Module(
    includes = ([TestSupportModule::class])
)
class BioDaoTestModule {
    @Single
    fun provideBioSchema(): BioMicroSchema {
        throw IllegalStateException("Should be overridden by test wrapper")
    }
    @Single
    fun provideBioDao(db: BioMicroSchema): BioDao = db.bioDao()
}
