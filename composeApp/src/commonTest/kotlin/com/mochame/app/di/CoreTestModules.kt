
package com.mochame.app.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.data.local.room.FakeSettingsStore
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
import kotlinx.coroutines.sync.Mutex
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

    val fakeSettingsStore = module {
        single<FakeSettingsStore> { FakeSettingsStore() }
        single<SettingsStore> { get<FakeSettingsStore>() }
    }

    @OptIn(ExperimentalKermitApi::class)
    fun testLoggingModule(platformTag: String) = module {
        single<TestLogWriter> { TestLogWriter(Severity.Verbose) }

        single<Logger>(named("RootLogger")) {
            Logger(
                config = StaticConfig(
                    logWriterList = listOf(
                        get<TestLogWriter>(),
                        CleanLogWriter()
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

        single<Mutex>(named("JanitorMutex")) {
            Mutex()
        }
        singleOf(::JanitorTestEnvironment)
    }


}

// -----------------------------------------------------------
// TEST ENVIRONMENTS
// -----------------------------------------------------------

@ExperimentalKermitApi
data class JanitorTestEnvironment(
    val janitor: SyncJanitor,
    val writer: TestLogWriter,
    val statusProvider: BootStatusProvider,
    val hlcFactory: HlcFactory,
    val metadataStore: MetadataStoreMaintenance,
    val ledgerMaintenance: MutationLedgerMaintenance,
    val transactor: TransactionProvider
)