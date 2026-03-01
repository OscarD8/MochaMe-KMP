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
    /**
     * THE UNIVERSAL BRAIN (DAOs)
     * These are the "Synapses."
     * The Brain asks the 'MochaDatabase' (The Heart) to provide these tools.
     * It doesn't care if the Heart is on Android or Linux;
     * it just knows it can call 'telemetryDao()' once the Heart is beating.
     */
    single { get<MochaDatabase>().telemetryDao() }
    single { get<MochaDatabase>().bioDao() }

    /**
     * THE REPOSITORIES
     * These are the "Reflexes."
     * They take the raw data Synapses (DAOs) and wrap them in clean logic.
     * They are 'single' because we only need one set of reflexes for the whole life.
     */
    single<TelemetryRepository> { TelemetryRepositoryImpl(get()) }
    single<BioRepository> { BioRepositoryImpl(get()) }

    /**
     * THE CONSCIOUSNESS (ViewModel)
     * This is the highest level of the organism's thought process.
     * We use 'viewModel' instead of 'factory' because this part of the brain
     * needs to stay alive during "Environmental Shifts" (like rotating a phone).
     * It asks the Ribosome for both the Telemetry and Bio reflexes.
     */
    viewModel { ProofOfLifeViewModel(get(), get()) }
}