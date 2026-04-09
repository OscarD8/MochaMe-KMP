package com.mochame.app.di.modules

import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.RoomImmediateTransProvider
import com.mochame.app.data.local.room.RoomSettingsStore
import com.mochame.app.data.local.room.feature.bio.RoomBioRepository
import com.mochame.app.data.local.room.feature.resonance.RoomResonanceRepository
import com.mochame.app.data.local.room.feature.telemetry.RoomTelemetryRepository
import com.mochame.app.data.local.room.feature.telemetry.bridge.AnalyticsBridge
import com.mochame.app.data.local.room.feature.telemetry.bridge.ContextBridge
import com.mochame.app.data.local.room.feature.telemetry.bridge.MomentBridge
import com.mochame.app.data.local.room.sync.RoomMetadataStore
import com.mochame.app.data.local.room.sync.RoomMutationLedger
import com.mochame.app.di.providers.AppPaths
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.feature.bio.BioRepository
import com.mochame.app.domain.feature.bio.DailyContext
import com.mochame.app.domain.feature.resonance.ResonanceRepository
import com.mochame.app.domain.feature.telemetry.repositories.TelemetryRepository
import com.mochame.app.domain.sync.PayloadEncoder
import com.mochame.app.domain.sync.SyncGateway
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.stores.BlobReader
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.domain.sync.stores.MetadataStore
import com.mochame.app.domain.sync.stores.MetadataStoreMaintenance
import com.mochame.app.domain.sync.stores.MutationLedger
import com.mochame.app.domain.sync.stores.MutationLedgerMaintenance
import com.mochame.app.domain.sync.usecase.PruneOldEntriesUseCase
import com.mochame.app.domain.system.settings.SettingsStore
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.LogTags
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.sync.RealBlobStore
import com.mochame.app.infrastructure.sync.bio.BioPayloadCodecRegistry
import com.mochame.app.infrastructure.sync.bio.BioPayloadEncoderV1
import com.mochame.app.infrastructure.system.KeyedLocker
import com.mochame.app.infrastructure.system.SqliteResiliencePolicy
import com.mochame.app.infrastructure.system.boot.BootStatusManager
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.utils.Hasher
import com.mochame.app.infrastructure.utils.sha256Hasher
import com.mochame.app.orchestration.sync.SyncJanitor
import com.mochame.app.ui.ProofOfLifeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
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
            systemModule,         // BootStatusManager (Updater/Provider)
            encoderModule,         // All domains and their encoders
            blobModule,            // RealBlobStore + a factory method for Digest objects

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
            productionStartupModule,
            commonModule,
            policiesModule
        )
    }

    val commonModule = module {
        single(named("AppScope")) {
            val dispatchers = get<DispatcherProvider>()
            CoroutineScope(SupervisorJob() + dispatchers.main)
        }
    }

    val policiesModule = module {

        single {
            SqliteResiliencePolicy(
                logger = get { parametersOf(LogTags.Layer.DATA, LogTags.Domain.EXECUTE) }
            )
        }.bind(ExecutionPolicy::class)
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
        singleOf(::RoomMutationLedger) {
            bind<MutationLedger>()
            bind<MutationLedgerMaintenance>()
        }
        singleOf(::RoomMetadataStore) {
            bind<MetadataStore>()
            bind<MetadataStoreMaintenance>()
        }
        singleOf(::RoomImmediateTransProvider) {
            bind<TransactionProvider>()
        }
        single {
            PruneOldEntriesUseCase(
                logger = get { parametersOf(LogTags.Domain.PRUNE, LogTags.Layer.DOMAIN) },
                ledgerMaintenance = get(),
                dateTimeUtils = get()
            )
        }

        // Infrastructure DAOs used by the components defined by the
        single { get<MochaDatabase>().syncMetadataDao() }
        single { get<MochaDatabase>().mutationLedgerDao() }
    }

    /** --- INFRASTRUCTURE LAYER --- */

    val timeModule = module {
        singleOf(::DateTimeUtils)
    }

    val identityModule = module {
        single(named("IdentityMutex")) { Mutex() }

        singleOf(::RoomSettingsStore) {
            bind<SettingsStore>()
        }

        single { get<MochaDatabase>().settingsDao() }

        single {
            IdentityManager(
                settingsStore = get(),
                dispatcherProvider = get(),
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.BOOT) },
                mutex = get(named("IdentityMutex"))
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

    val systemModule = module {
        singleOf(::KeyedLocker)

        singleOf(::BootStatusManager) {
            bind<BootStatusUpdater>()
            bind<BootStatusProvider>()
        }
    }

    val janitorModule = module {
        single(named("JanitorMutex")) { Mutex() }

        single<SyncJanitor> {
            val scope: CoroutineScope = get(named("AppScope"))

            SyncJanitor(
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.ORCH) },
                appScope = scope,

                // Regular dependencies
                identityManager = get(),
                dispatcher = get(),
                hlcFactory = get(),
                bootUpdater = get(),
                transactor = get(),
                metadataStore = get(),
                ledgerStore = get(),
                pruneUseCase = get(),
                mutex = get(named("JanitorMutex")),
                executor = get(),
                blobAdmin = get<BlobStager>()
            )
        }
    }

    val blobModule = module {
        single<Hasher> { sha256Hasher() }
        single(named("BlobMutex")) { Mutex() }

        single<RealBlobStore> {
            val paths = get<AppPaths>()
            RealBlobStore(
                dateTimeUtils = get(),
                pendingDir = Path(paths.blobPending),
                committedDir = Path(paths.blobCommitted),
                hashProvider = get<Hasher>(),
                dispatcher = get<DispatcherProvider>(),
                fileSystem = get<FileSystem>(),
                logger = get { parametersOf(LogTags.Domain.SYNC, LogTags.Layer.INFRA) },
                initMutex = get(named("BlobMutex"))
            )
        }.binds(arrayOf(BlobStager::class, BlobReader::class))
    }

    val encoderModule = module {
        single {
            BioPayloadEncoderV1(
                logger = get { parametersOf(LogTags.Domain.BIO, LogTags.Layer.INFRA) },
                bufferProvider = get()
            )
        }

        single<PayloadEncoder<DailyContext>> {
            BioPayloadCodecRegistry(
                v1 = get(),
                logger = get { parametersOf(LogTags.Domain.BIO, LogTags.Layer.INFRA) }
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
                mutationLedger = get(),
                executor = get(),
                dispatcher = get(),
                encoder = get<PayloadEncoder<DailyContext>>(),
                blobStore = get(),
                locker = get()
            )
        }.binds(arrayOf(BioRepository::class, SyncGateway::class))
    }

    val signalDataModule = module {
        single { get<MochaDatabase>().signalDao() }
        singleOf(::RoomResonanceRepository) { bind<ResonanceRepository>() }
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
            systemModule,
            syncPlumbingModule,
            janitorModule,
            policiesModule,
            blobModule
        )
    }

}

