@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.orchestration

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.metadata.MochaModule
import com.mochame.platform.global.GlobalMetadataDao
import com.mochame.support.runTestWithPersistence
import com.mochame.sync.data.daos.MutationLedgerDao
import com.mochame.sync.data.daos.SyncMetadataDao
import com.mochame.sync.database.SyncDatabaseConstructor
import com.mochame.sync.database.SyncTestDatabase
import com.mochame.sync.di.JanitorTestApp
import com.mochame.sync.di.JanitorTestEnvironment
import com.mochame.sync.di.JanitorTestModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.koin.dsl.includes
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
inline fun runJanitorTest(crossinline block: JanitorTestEnvironment.(TestScope) -> Unit) =
    runTestWithPersistence<SyncTestDatabase, JanitorTestEnvironment>(
        constructor = SyncDatabaseConstructor,
        koinSetup = { includes(koinConfiguration<JanitorTestApp>()) },
        daoWiring = {
            module {
                single<SyncMetadataDao> { syncMetadataDao() }
                single<MutationLedgerDao> { mutationLedgerDao() }
                single<GlobalMetadataDao> { globalMetaDao() }
            }
        },
        block = block
    )


@ExperimentalCoroutinesApi
class SyncJanitorTest {

    // -----------------------------------------------------------
    // LOGGING
    // -----------------------------------------------------------
    @Test
    fun should_log_correct_seeding_count_when_new_install() = runJanitorTest { scope ->
        val count = MochaModule.entries.size

        janitor.startupChecks()
        scope.advanceUntilIdle()

        val seedingMessage =
            writer.logs.find { it.message.contains("Seeded $count missing") }
        assertNotNull(seedingMessage, "The success log should have been recorded!")
        assertEquals(Severity.Info, seedingMessage.severity)
    }

}