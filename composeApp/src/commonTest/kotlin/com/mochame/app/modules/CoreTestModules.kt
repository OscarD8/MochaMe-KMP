package com.mochame.app.modules

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.core.CleanLogWriter
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.FakeDateTimeUtils
import com.mochame.app.core.HlcFactory
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.domain.repository.sync.SyncJanitor
import com.mochame.app.domain.system.BootStatusManager
import com.mochame.app.domain.system.BootStatusProvider
import com.mochame.app.domain.system.BootStatusUpdater
import com.mochame.app.domain.system.IdentityManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

data class JanitorTestEnvironment(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val statusProvider: BootStatusProvider,
    val metaDataDao: SyncMetadataDao,
    val ledgerDao: MutationLedgerDao,
    val db: MochaDatabase,
    val hlcFactory: HlcFactory
)

object CoreTestModules {

    // -----------------------------------------------------------
    // UTILS
    // -----------------------------------------------------------
    val dateTimeUtilsModule = module {
        single<FakeDateTimeUtils> { FakeDateTimeUtils() }
        single<DateTimeUtils> { get<FakeDateTimeUtils>() }
    }

    val hlcFactoryModule = module {
        singleOf(::HlcFactory)
    }

    @OptIn(ExperimentalKermitApi::class)
    val loggerModule = module {
        single<TestLogWriter> { TestLogWriter(Severity.Verbose) }
        single<Logger> {
            Logger(
                config = StaticConfig(
                    logWriterList = listOf(
                        get<TestLogWriter>(),
                        CleanLogWriter()
                    )
                ),
                tag = "Core-Test"
            )
        }
    }

    // -----------------------------------------------------------
    // DAO
    // -----------------------------------------------------------
    val bioDaoModule = module {
        single { get<MochaDatabase>().bioDao() }
    }
    val metadataDaoModule = module {
        single { get<MochaDatabase>().syncMetadataDao() }
    }
    val ledgerDaoModule = module {
        single { get<MochaDatabase>().mutationLedgerDao() }
    }
    val settingDaoModule = module {
        single { get<MochaDatabase>().settingsDao() }
    }

    // -----------------------------------------------------------
    // CLASSES
    // -----------------------------------------------------------
    val identityManagerModule = module {
        singleOf(::IdentityManager)
    }

    val bootStatusModule = module {
        singleOf(::BootStatusManager) {
            bind<BootStatusUpdater>()
            bind<BootStatusProvider>()
        }
    }

    val janitorModule = module {
        singleOf(::JanitorTestEnvironment)

        single<SyncJanitor> {
            SyncJanitor(
                bootUpdater = get(),
                database = get(),
                metadataDao = get(),
                ledgerDao = get(),
                identityManager = get(),
                dispatcherProvider = get(),
                appScope = get(named("AppScope")),
                hlcFactory = get(),
                logger = get()
            )
        }
    }



    // -----------------------------------------------------------
    // PACKAGES
    // -----------------------------------------------------------
    val testSyncInfraModule = module {
        single { get<MochaDatabase>().mutationLedgerDao() }
        single { get<MochaDatabase>().syncMetadataDao() }
        single { get<MochaDatabase>().syncTombstoneDao() }
    }

    /**
     * Note on this you still need to provide the specific test scope and dispatcher.
     */
    val syncTestSystemModule = module {
        includes(
            hlcFactoryModule,
            identityManagerModule,
            metadataDaoModule,
            ledgerDaoModule,
            settingDaoModule,
            dateTimeUtilsModule,
            bootStatusModule
        )
    }

}