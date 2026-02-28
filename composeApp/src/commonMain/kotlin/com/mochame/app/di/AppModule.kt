package com.mochame.app.di

import com.mochame.app.ui.ProofOfLifeViewModel
import com.mochame.app.data.telemetry.TelemetryRepositoryImpl
import com.mochame.app.data.bio.BioRepositoryImpl
import com.mochame.app.database.MochaDatabase
import com.mochame.app.domain.telemetry.TelemetryRepository
import com.mochame.app.domain.bio.BioRepository
import org.koin.dsl.module
// THE UPDATED IMPORT FOR KOIN 4.0
import org.koin.core.module.dsl.viewModel

val appModule = module {
    // 1. Provide DAOs
    single { get<MochaDatabase>().telemetryDao() }
    single { get<MochaDatabase>().bioDao() }

    // 2. Provide Repositories
    single<TelemetryRepository> { TelemetryRepositoryImpl(get()) }
    single<BioRepository> { BioRepositoryImpl(get()) }

    // 3. Provide ViewModel
    // This now resolves correctly via the new DSL package
    viewModel { ProofOfLifeViewModel(get(), get()) }
}