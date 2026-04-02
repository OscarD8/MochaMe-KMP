package com.mochame.app.data.local.room

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Severity
import com.mochame.app.data.local.room.entity.MutationLedgerEntity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.SyncPersistenceTestEnv
import com.mochame.app.di.modules.AppModules
import com.mochame.app.domain.exceptions.MochaException
import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.domain.system.sync.utils.MutationOp
import com.mochame.app.domain.system.sync.utils.SyncStatus
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HLCTools
import com.mochame.app.utils.establishTestScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExperimentalKermitApi
abstract class BaseSyncPersistenceTest : KoinTest {

    // -----------------------------------------------------------
    // MODULES AND COMPONENTS
    // -----------------------------------------------------------
    abstract val platformTestModules: List<Module>
    private val coreTestModules: List<Module> = listOf(
        AppModules.syncPlumbingModule,
        AppModules.policiesModule,
        CoreTestModules.syncPersistenceTestModule,
        CoreTestModules.testLoggingModule(minSeverity = Severity.Verbose)
    )

    private val hlc: HLC = HLC(
        HLCTools.TEST_APP_RELEASE_MS,
        1,
        "node1"
    )
    private val pendingMutation = MutationLedgerEntity(
        hlc = hlc.toString(),
        syncId = "STALE_ID",
        syncStatus = SyncStatus.PENDING,
        candidateKey = "1",
        entityType = MochaModule.BIO,
        operation = MutationOp.DELETE,
    )

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun start_koin_context() {
        startKoin {
            allowOverride(true)
            modules(coreTestModules + platformTestModules)
        }
    }

    @AfterTest
    fun stop_koin_context() {
        stopKoin()
    }

    fun runTestWrapper(block: suspend SyncPersistenceTestEnv.(TestScope) -> Unit) = runTest {
        val testDispatcher = this.establishTestScope()

        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val env: SyncPersistenceTestEnv by inject()

        try {
            env.block(this)
        } finally {
            env.writer.reset()
            db.close()
        }
    }

    // -----------------------------------------------------------
    // SUCCESS PATH
    // -----------------------------------------------------------
    @Test
    fun should_verify_system_state_after_maintenance_cycle() = runTestWrapper { scope ->
        // Arrange: Simulate a dirty state (as the Janitor would find it)
        val mutation = pendingMutation.copy(syncStatus = SyncStatus.SYNCING)
        ledgerStore.recordIntent(mutation)

        metadataStore.ensureSeeded()
        metadataStore.recordPendingMetadata(MochaModule.BIO, hlc)
        metadataStore.updatePendingToSyncing(MochaModule.BIO)

        // Act: Perform the "Janitor" style cleanup calls
        val resetMetadataCount = metadataStore.bulkResetDirtyModules()
        val clearedLedgerLocks = ledgerStore.clearAllLocksAndResetToPending()

        // Assert: Verify both tables reached the expected "Idle/Pending" state
        assertEquals(1, resetMetadataCount, "Metadata module should have been reset.")
        assertEquals(1, clearedLedgerLocks, "Stale ledger lock should have been cleared.")

        val bioMetadata = metadataStore.getModuleMetadata(MochaModule.BIO)
        assertEquals(
            SyncStatus.PENDING,
            bioMetadata?.syncStatus,
            "Metadata should be PENDING after reset."
        )

        val ledgerEntries = ledgerStore.getPendingByModule(MochaModule.BIO)
        assertEquals(
            1,
            ledgerEntries.size,
            "Single ledger entry at pending state expected."
        )
        assertEquals(
            mutation.candidateKey,
            ledgerEntries.first()?.candidateKey,
            "The ledger fetched a candidate key that doesn't represent the original mutation."
        )
    }



    // -----------------------------------------------------------
    // FAILURES/THROWS
    // -----------------------------------------------------------
    @Test
    fun should_throw_contention_when_call_to_reset_active_sync() = runTestWrapper {
        // --- Arrange ---
        // 1. Initialize the board with all modules
        metadataStore.ensureSeeded()

        // 2. Simulate a local write to move BIO from IDLE -> PENDING
        metadataStore.recordPendingMetadata(MochaModule.BIO, hlc)

        // 3. Orchestrator starts a sync: PENDING -> SYNCING
        metadataStore.updatePendingToSyncing(MochaModule.BIO)

        // Verify setup: BIO should be locked for sync
        val preJanitorState = metadataStore.getModuleMetadata(MochaModule.BIO)
        assertEquals(SyncStatus.SYNCING, preJanitorState?.syncStatus)

        // --- Act ---
        // 1. The Janitor interrupts (Simulating a crash recovery or maintenance sweep)
        metadataStore.bulkResetDirtyModules()

        // 2. The zombie attempts to finalize its work
        val exception = assertFailsWith<MochaException.Transient.Contention> {
            metadataStore.updateSyncingToSuccess(MochaModule.BIO)
        }

        // --- Assert ---
        assertTrue(
            exception.message.contains("Contention on BIO"),
            "Exception message should clearly identify the module conflict."
        )

        val finalState = metadataStore.getModuleMetadata(MochaModule.BIO)
        assertEquals(
            SyncStatus.PENDING,
            finalState?.syncStatus,
            "The Janitor's pending status should have survived the contention."
        )
    }

    // -----------------------------------------------------------
    // CONCURRENCY/ATOMICITY
    // -----------------------------------------------------------
    @Test
    fun should_seed_exactly_once_when_called_multiple_times() = runTestWrapper {
        // --- Arrange ---
        val initialCount = metadataStore.getMetadataCount()
        assertEquals(0, initialCount, "Database must be empty before starting the seed test.")

        val expectedModuleCount = MochaModule.entries.size

        // --- Act ---
        // First attempt: This should perform the actual inserts.
        val firstSeedResult = metadataStore.ensureSeeded()

        // Second attempt: This should detect existing rows and do nothing.
        val secondSeedResult = metadataStore.ensureSeeded()

        // --- Assert ---
        // 1. Verify the first call returned the total number of modules defined in the system.
        assertEquals(
            expectedModuleCount,
            firstSeedResult,
            "The first seed call should report inserting all defined modules."
        )

        // 2. Verify the second call returned 0, acknowledging no new work was done.
        assertEquals(
            0,
            secondSeedResult,
            "The second seed call should be idempotent and return 0."
        )

        // 3. Verify the final database state strictly matches our module definitions.
        val finalCount = metadataStore.getMetadataCount()
        assertEquals(
            expectedModuleCount,
            finalCount,
            "The final database row count must exactly match the number of MochaModules."
        )
    }


}
