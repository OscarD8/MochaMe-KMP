package com.mochame.app.di

import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.HlcFactory
import com.mochame.app.data.repository.BioRepositoryImpl
import com.mochame.app.data.repository.SignalRepositoryImpl
import com.mochame.app.ui.ProofOfLifeViewModel
import com.mochame.app.data.repository.telemetry.TelemetryRepositoryImpl
import com.mochame.app.data.repository.telemetry.bridge.AnalyticsBridge
import com.mochame.app.data.repository.telemetry.bridge.ContextBridge
import com.mochame.app.data.repository.telemetry.bridge.MomentBridge
import com.mochame.app.database.MochaDatabase
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.model.telemetry.Moment
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.SignalRepository
import com.mochame.app.domain.repository.telemetry.TelemetryRepository
import com.mochame.app.domain.sync.SyncCoordinator
import com.mochame.app.domain.sync.SyncGateway
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.bind

val appModule = module {

    /** --- THE TOOLS --- */
    singleOf(::DateTimeUtils) // Auto-wires constructor params
    singleOf(::HlcFactory)

    singleOf(::BioRepositoryImpl) {
        // Here we 'tag' the instance with the roles it plays
        bind<BioRepository>() // Scenario A: The UI
        bind<SyncGateway<DailyContext>>() // Scenario B: The Sync
    }
//
//    singleOf(::TelemetryRepositoryImpl) {
//        bind<TelemetryRepository>()
//        bind<SyncGateway<Moment>>()
//    }

    single {
        SyncCoordinator(
            // Koin 4.0 collects all the 'binds' into this list automatically
            gateways = getAll<SyncGateway<*>>(),
//            cloudApi = get()
        )
    }

    /** --- THE DAOS --- */
    // Scoped specifically to the Database instance
    single { get<MochaDatabase>().telemetryDao() }
    single { get<MochaDatabase>().bioDao() }
    single { get<MochaDatabase>().signalDao() }
    single { get<MochaDatabase>().syncTombstoneDao() }

    /** * --- THE INTERNAL BRIDGES ---
     * We do NOT give these public interfaces here.
     * We keep them as their concrete types so ONLY the Repository can find them.
     */
    factory { ContextBridge(telemetryDao = get()) }
    factory { AnalyticsBridge(dao = get()) }
    factory {
        MomentBridge(
            dao = get(),
            dateTimeUtils = get()
        )
    }

    /** --- THE PUBLIC REPOSITORIES (The Seal) --- */
    single<TelemetryRepository> {
        TelemetryRepositoryImpl(
            context = get<ContextBridge>(),    // Explicitly fetching the bridge
            moment = get<MomentBridge>(),
            analytics = get<AnalyticsBridge>()
        )
    }

    single<BioRepository> {
        BioRepositoryImpl(
            bioDao = get(),
            dateTimeUtils = get(),
            dispatchers = get()
        )
    }

    single<SignalRepository> { SignalRepositoryImpl(signalDao = get(), dateTimeUtils = get()) }

    /** --- UI LAYER --- */
    viewModelOf(::ProofOfLifeViewModel)
}