
package com.mochame.app.di

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.infrastructure.logging.CleanLogWriter
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.utils.FakeDateTimeUtils
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.dao.sync.MutationLedgerDao
import com.mochame.app.data.local.room.dao.sync.SyncMetadataDao
import com.mochame.app.infrastructure.sync.SyncJanitor
import com.mochame.app.infrastructure.system.boot.BootStatusManager
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.system.boot.BootStatusUpdater
import com.mochame.app.infrastructure.identity.IdentityManager
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module


object TestTag {
    val CORE = "CoreTest"
    val JVM = "JVMTest"
    val ANDROIDEVICE = "AndroidDeviceTest"
    val ANDROIDHOST = "AndroidHostTest"
}

object CoreTestModules {
    // -----------------------------------------------------------
    // UTILS
    // -----------------------------------------------------------
    val fakeDateTimeUtilsModule = module {
        single<FakeDateTimeUtils> { FakeDateTimeUtils() }
        single<DateTimeUtils> { get<FakeDateTimeUtils>() }
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
    val metaDataDao: SyncMetadataDao,
    val ledgerDao: MutationLedgerDao,
    val db: MochaDatabase,
    val hlcFactory: HlcFactory
)

