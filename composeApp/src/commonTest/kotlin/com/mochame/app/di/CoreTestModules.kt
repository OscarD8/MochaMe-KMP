
package com.mochame.app.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.data.local.room.FakeLatentSettingsStore
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.RoomMetadataStore
import com.mochame.app.data.local.room.RoomMutationLedger
import com.mochame.app.data.local.room.RoomSettingsStore
import com.mochame.app.data.local.room.dao.SettingsDao
import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.di.providers.DispatcherProvider
import com.mochame.app.domain.system.settings.SettingsStore
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.domain.system.sync.MetadataStoreMaintenance
import com.mochame.app.domain.system.sync.MutationLedgerMaintenance
import com.mochame.app.domain.system.sync.TransactionProvider
import com.mochame.app.domain.system.sync.usecase.PruneOldEntriesUseCase
import com.mochame.app.infrastructure.fakeutils.FakeDateTimeUtils
import com.mochame.app.infrastructure.identity.IdentityManager
import com.mochame.app.infrastructure.logging.CleanLogWriter
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.orchestration.sync.SyncJanitor
import kotlinx.coroutines.sync.Mutex
import org.koin.core.module.dsl.singleOf
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module


object TestTag {
    const val CORE = "CoreTest"
    const val JVM = "JVMTest"
    const val ANDROID_DEVICE = "AndroidDeviceTest"
    const val ANDROID_HOST = "AndroidHostTest"
}

object CoreTestModules {
    // -----------------------------------------------------------
    // FAKES
    // -----------------------------------------------------------
    val fakeDateTimeUtilsModule = module {
        single<FakeDateTimeUtils> { FakeDateTimeUtils() }
        single<DateTimeUtils> { get<FakeDateTimeUtils>() }
    }

    val fakeLatentSettingsStore = module {
        single<FakeLatentSettingsStore> { FakeLatentSettingsStore() }
        single<SettingsStore> { get<FakeLatentSettingsStore>() }
    }

    @OptIn(ExperimentalKermitApi::class)
    fun testLoggingModule(
        platformTag: String = TestTag.JVM,
        minSeverity: Severity = Severity.Verbose
    ) = module {
        single<TestLogWriter> { TestLogWriter(minSeverity) }

        single<Logger>(named("RootLogger")) {
            Logger(
                config = StaticConfig(
                    logWriterList = listOf(
                        get<TestLogWriter>(),
                        CleanLogWriter(minSeverity),
                    )
                ),
                tag = platformTag
            )
        }

        factory { (domain: String, layer: String) ->
            val root = get<Logger>(named("RootLogger"))
            root.withTag("${root.tag} ❯ $layer ❯ $domain")
        }
    }

    @OptIn(ExperimentalKermitApi::class)
    val janitorTestEnvironmentModule = module {
        includes(
            fakeDateTimeUtilsModule
        )

        single {
            JanitorTestEnvironment(
                janitor = get(),
                writer = get(),
                bootUpdater = get(),
                hlcFactory = get(),
                metadataStore = get(),
                ledgerMaintenance = get(),
                transactor = get(),
                metadataDao = get(),
                manager = get(),
                settingsDao = get(),
                settingsStore = get(),
                executor = get(),
                logger = get { parametersOf("Sync", "Janitor") },
                dispatcherProvider = get(),
                pruneUseCase = get(),
                mutex = get<Mutex>(named("JanitorMutex"))
            )
        }
    }

    @OptIn(ExperimentalKermitApi::class)
    val hlcTestEnvironmentModule = module {
        includes(
            fakeDateTimeUtilsModule,
        )
        single<SyncMetadataDao> { get<MochaDatabase>().syncMetadataDao() }
        singleOf(::HLCTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val identityTestEnvironmentModule = module {
        singleOf(::IdentityTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val syncPersistenceTestModule = module {
        singleOf(::SyncPersistenceTestEnv)
    }
}

// -----------------------------------------------------------
// TEST ENVIRONMENTS
// -----------------------------------------------------------

@ExperimentalKermitApi
data class JanitorTestEnvironment(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val bootUpdater: BootStatusUpdater,
    val hlcFactory: HlcFactory,
    val metadataStore: MetadataStoreMaintenance,
    val ledgerMaintenance: MutationLedgerMaintenance,
    val transactor: TransactionProvider,
    val metadataDao: SyncMetadataDao,
    val manager: IdentityManager,
    val settingsDao: SettingsDao,
    val settingsStore: SettingsStore,
    val executor: ExecutionPolicy,
    val logger: Logger,
    val dispatcherProvider: DispatcherProvider,
    val pruneUseCase: PruneOldEntriesUseCase,
    val mutex: Mutex
)

@ExperimentalKermitApi
data class HLCTestEnvironment(
    val factory: HlcFactory,
    val writer: TestLogWriter,
    val fakeClock: FakeDateTimeUtils
)

@ExperimentalKermitApi
data class IdentityTestEnvironment(
    val manager: IdentityManager,
    val settingsStore: SettingsStore,
    val roomSettingsStore: RoomSettingsStore,
    val settingsDao: SettingsDao,
    val db: MochaDatabase,
    val executor: ExecutionPolicy,
    val writer: TestLogWriter
)

@ExperimentalKermitApi
data class SyncPersistenceTestEnv(
    val executor: ExecutionPolicy,
    val ledgerDao: MutationLedgerDao,
    val metadataDao: SyncMetadataDao,
    val writer: TestLogWriter,
    val db: MochaDatabase,
    val ledgerStore: RoomMutationLedger,
    val metadataStore: RoomMetadataStore
)