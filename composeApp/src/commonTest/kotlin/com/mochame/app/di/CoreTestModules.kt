
package com.mochame.app.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.data.local.room.FakeLatentSettingsStore
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.domain.system.settings.SettingsStore
import com.mochame.app.infrastructure.logging.CleanLogWriter
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.fakeutils.FakeDateTimeUtils
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.domain.system.sync.MetadataStoreMaintenance
import com.mochame.app.domain.system.sync.MutationLedgerMaintenance
import com.mochame.app.domain.system.sync.TransactionProvider
import com.mochame.app.orchestration.sync.SyncJanitor
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import org.koin.core.module.dsl.singleOf
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

        singleOf(::JanitorTestEnvironment)
    }

    @OptIn(ExperimentalKermitApi::class)
    val hlcTestEnvironmentModule = module {
        includes(
            fakeDateTimeUtilsModule,
        )
        single<SyncMetadataDao> { get<MochaDatabase>().syncMetadataDao() }
        singleOf(::HLCTestEnvironment)
    }

}

// -----------------------------------------------------------
// TEST ENVIRONMENTS
// -----------------------------------------------------------

@ExperimentalKermitApi
data class JanitorTestEnvironment(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val bootUpdater: BootStatusProvider,
    val hlcFactory: HlcFactory,
    val metadataStore: MetadataStoreMaintenance,
    val ledgerMaintenance: MutationLedgerMaintenance,
    val transactor: TransactionProvider,
    val metadataDao: SyncMetadataDao
)

@ExperimentalKermitApi
data class HLCTestEnvironment(
    val factory: HlcFactory,
    val writer: TestLogWriter,
    val fakeClock: FakeDateTimeUtils
)