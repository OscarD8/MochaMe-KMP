package com.mochame.app.di.modules

import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.data.local.room.RoomMetadataStore
import com.mochame.app.data.local.room.RoomMutationLedger
import com.mochame.app.data.local.room.RoomTransactionProvider
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.logging.LogTags
import com.mochame.app.data.repository.bio.RoomBioRepository
import com.mochame.app.data.repository.signal.RoomSignalRepository
import com.mochame.app.data.repository.telemetry.RoomTelemetryRepository
import com.mochame.app.data.repository.telemetry.bridge.AnalyticsBridge
import com.mochame.app.data.repository.telemetry.bridge.ContextBridge
import com.mochame.app.data.repository.telemetry.bridge.MomentBridge
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.domain.bio.BioRepository
import com.mochame.app.domain.signal.SignalRepository
import com.mochame.app.domain.sync.MetadataStore
import com.mochame.app.domain.sync.MutationLedger
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.SyncGateway
import com.mochame.app.domain.telemetry.repositories.TelemetryRepository
import com.mochame.app.infrastructure.system.boot.BootStatusManager
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.sync.SyncJanitor
import com.mochame.app.ui.ProofOfLifeViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/** --- UI LAYER --- */

object AppModules {

    /** --- THE GRID. DON'T FORGET TO ADD!!!!!! --- */
    val allAppModules = module {
        includes(
            /** 1. INFRASTRUCTURE: Foundational Tools (Stateless/Core) */
            timeModule,         // DateTimeUtils, Clocks
            identityModule,     // IdentityManager, SettingsDao
            hlcModule,          // HlcFactory
            bootModule,         // BootStatusManager (Updater/Provider)

            /** 2. DATA & ENGINE: The Sync Plumbing (Stateful Infrastructure) */
            syncPlumbingModule, // Room Ledger, Transactions, Metadata Store
            janitorModule,      // SyncJanitor (Lazy by default)

            /** 3. DOMAIN FEATURES: Business Logic & Local Repositories */
            bioDataModule,      // Bio Repository & DAO
            signalDataModule,   // Signal Repository & DAO
            telemetryDataModule,// Telemetry, Analytics, Bridges

            /** 4. PRESENTATION: UI & Interaction */
            uiModule,           // ViewModels

            /** 5. LIFECYCLE: Production-Only Automation */
            productionStartupModule
        )
    }

    /**
     * Only loaded in the real App (not in tests).
     * This "kicks" the lazy singletons into action at boot.
     */
    val productionStartupModule = module {
        single<SyncJanitor>(createdAtStart = true) { get() }
    }

    /** --- UI LAYER --- */
    val uiModule = module {
        viewModelOf(::ProofOfLifeViewModel)
    }

    /** --- DATA LAYER --- */
    val syncPlumbingModule = module {
        // These are the parts the repositories need to function
        single<MutationLedger> { RoomMutationLedger(get()) }
        single<TransactionProvider> { RoomTransactionProvider(get()) }
        single<MetadataStore> { RoomMetadataStore(get()) }

        // Infrastructure DAOs used by the components defined by the
        single { get<MochaDatabase>().syncMetadataDao() }
        single { get<MochaDatabase>().mutationLedgerDao() }
        single { get<MochaDatabase>().syncTombstoneDao() }
    }

    /** --- INFRASTRUCTURE LAYER --- */
    val timeModule = module {
        singleOf(::DateTimeUtils)
    }

    val identityModule = module {
        single { get<MochaDatabase>().settingsDao() }
        single {
            IdentityManager(
                settingsDao = get(),
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.BOOT) },
                dispatcherProvider = get()
            )
        }
    }

    val hlcModule = module {
        single {
            HlcFactory(
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA) },
                dateTimeUtils = get()
            )
        }
    }

    val bootModule = module {
        singleOf(::BootStatusManager) {
            bind<BootStatusUpdater>()
            bind<BootStatusProvider>()
        }
    }

    val janitorModule = module {
        single<SyncJanitor> {
            val scope: CoroutineScope = get(named("AppScope"))

            SyncJanitor(
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA) },
                appScope = scope,

                // Regular dependencies
                identityManager = get(),
                dispatcherProvider = get(),
                hlcFactory = get(),
                database = get(),
                bootUpdater = get(),
                metadataDao = get(),
                ledgerDao = get()
            )
        }
    }


//    single {
//        SyncCoordinator(
//            // Koin 4.0 collects all the 'binds' into this list automatically
//            gateways = getAll<SyncGateway<*>>(),
////            cloudApi = get()
//        )
//    }

    /** --- FEATURE SPECIFIC MODULES --- */
    val bioDataModule = module {
        single { get<MochaDatabase>().bioDao() }

        single<RoomBioRepository> {
            RoomBioRepository(
                dateTimeUtils = get(),
                bioDao = get(),
                logger = get { parametersOf(LogTags.Domain.BIO, LogTags.Layer.DATA) },
                hlcFactory = get(),
                bootStatusProvider = get(),
                metadataStore = get(),
                transactor = get(),
                mutationLedger = get()
            )
        }.binds(arrayOf(BioRepository::class, SyncGateway::class))
    }

    val signalDataModule = module {
        single { get<MochaDatabase>().signalDao() }
        singleOf(::RoomSignalRepository) { bind<SignalRepository>() }
    }

    val telemetryDataModule = module {
        single { get<MochaDatabase>().telemetryDao() }
        singleOf(::RoomTelemetryRepository) { bind<TelemetryRepository>() }

        // Bridges for Telemetry
        singleOf(::ContextBridge)
        singleOf(::AnalyticsBridge)
        singleOf(::MomentBridge)
    }

    val janitorSetupModules = module {
        includes(
            timeModule,
            identityModule,
            hlcModule,
            bootModule,
            syncPlumbingModule,
            janitorModule
        )
    }

}

