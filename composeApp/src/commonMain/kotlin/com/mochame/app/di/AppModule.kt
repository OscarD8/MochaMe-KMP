package com.mochame.app.di

import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.HlcFactory
import com.mochame.app.data.repository.BioRepositoryImpl
import com.mochame.app.data.repository.SignalRepositoryImpl
import com.mochame.app.data.repository.telemetry.TelemetryRepositoryImpl
import com.mochame.app.data.repository.telemetry.bridge.AnalyticsBridge
import com.mochame.app.data.repository.telemetry.bridge.ContextBridge
import com.mochame.app.data.repository.telemetry.bridge.MomentBridge
import com.mochame.app.database.MochaDatabase
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.SignalRepository
import com.mochame.app.domain.repository.telemetry.TelemetryRepository
import com.mochame.app.domain.repository.sync.SyncCoordinator
import com.mochame.app.domain.repository.sync.SyncGateway
import com.mochame.app.domain.repository.sync.SyncJanitor
import com.mochame.app.domain.system.IdentityManager
import com.mochame.app.ui.ProofOfLifeViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {

    /** --- CORE & TOOLS --- */
    singleOf(::DateTimeUtils)
    singleOf(::HlcFactory)
    singleOf(::IdentityManager)

    /** --- CLOUD API (CURRENTLY MOCKED) --- */
//    single<MochaCloudApi> { MockCloudApi() }

    /** --- DAOS (From Database Instance) --- */
    single { get<MochaDatabase>().telemetryDao() }
    single { get<MochaDatabase>().bioDao() }
    single { get<MochaDatabase>().signalDao() }
    single { get<MochaDatabase>().syncTombstoneDao() }
    single { get<MochaDatabase>().syncMetadataDao() }
    single { get<MochaDatabase>().mutationLedgerDao() }
    single { get<MochaDatabase>().settingsDao() }

    /** --- REPOSITORIES (Multi-Role Implementation) --- */
    single<BioRepositoryImpl> {
        BioRepositoryImpl(
            dateTimeUtils = get(),
            bioDao = get(),
            logger = get(),
            metadataDao = get(),
            database = get(),
            hlcFactory = get(),
            ledgerDao = get()
        )
    }.binds(arrayOf(BioRepository::class, SyncGateway::class))

    singleOf(::TelemetryRepositoryImpl) {
        bind<TelemetryRepository>()
        // bind<SyncGateway<Moment>>() // Uncomment when ready
    }

    singleOf(::SignalRepositoryImpl) {
        bind<SignalRepository>()
        // bind<SyncGateway<Author>>() // Uncomment when ready
    }

    singleOf( ::ContextBridge )
    singleOf ( ::AnalyticsBridge )
    singleOf ( ::MomentBridge )

    /** --- SYNC LAYER --- */
    singleOf(::SyncJanitor) {
        createdAtStart()
    }

    single {
        SyncCoordinator(
            // Koin 4.0 collects all the 'binds' into this list automatically
            gateways = getAll<SyncGateway<*>>(),
//            cloudApi = get()
        )
    }

    /** --- UI LAYER --- */
    viewModelOf(::ProofOfLifeViewModel)
}