package com.mochame.app.di

import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.repository.BioRepositoryImpl
import com.mochame.app.ui.ProofOfLifeViewModel
import com.mochame.app.data.repository.telemetry.TelemetryRepositoryImpl
import com.mochame.app.data.repository.telemetry.bridge.ChronicleBridge
import com.mochame.app.data.repository.telemetry.bridge.IdentityBridge
import com.mochame.app.data.repository.telemetry.bridge.ObservationBridge
import com.mochame.app.database.MochaDatabase
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.telemetry.TelemetryRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
// THE UPDATED IMPORT FOR KOIN 4.0
import org.koin.core.module.dsl.viewModelOf

val appModule = module {

    /** --- THE TOOLS --- */
    singleOf(::DateTimeUtils) // 2026 DSL: Auto-wires constructor params

    /** --- THE SYNAPSES (DAOs) --- */
    // Scoped specifically to the Database instance
    single { get<MochaDatabase>().telemetryDao() }
    single { get<MochaDatabase>().bioDao() }

    /** * --- THE INTERNAL BRIDGES ---
     * We do NOT give these public interfaces here.
     * We keep them as their concrete types so ONLY the Repository can find them.
     */
    factory { IdentityBridge(telemetryDao = get()) }
    factory { ChronicleBridge(dao = get()) }
    factory {
        ObservationBridge(
            dao = get(),
            dateTimeUtils = get()
        )
    }

    /** --- THE PUBLIC REPOSITORIES (The Seal) --- */
    single<TelemetryRepository> {
        TelemetryRepositoryImpl(
            identity = get<IdentityBridge>(),    // Explicitly fetching the bridge
            observation = get<ObservationBridge>(),
            chronicle = get<ChronicleBridge>()
        )
    }

    single<BioRepository> {
        BioRepositoryImpl(
            bioDao = get(),
            dateTimeUtils = get()
        )
    }

    /** --- THE CONSCIOUSNESS --- */
    viewModelOf(::ProofOfLifeViewModel) // Clean, reflective, and K2-friendly
}