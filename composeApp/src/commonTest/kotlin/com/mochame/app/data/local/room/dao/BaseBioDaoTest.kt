package com.mochame.app.data.local.room.dao

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.app.data.local.room.MochaDatabase
import com.mochame.app.data.local.room.entities.DailyContextEntity
import com.mochame.app.di.CoreTestModules
import com.mochame.app.di.modules.AppModules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@ExperimentalKermitApi
abstract class BaseBioDaoTest : KoinTest {

    // -----------------------------------------------------------
    // MODULES
    // -----------------------------------------------------------
    abstract val platformTestModules: List<Module>
    private val coreTestModules: List<Module> = listOf(
        AppModules.bioDataModule,
        CoreTestModules.fakeDateTimeUtilsModule
    )

    // -----------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------
    private fun createHlc(ts: Long, count: Int = 0, node: String = "test-node"): String {
        return "$ts:$count:$node"
    }

    // -----------------------------------------------------------
    // SETUP/TEARDOWN
    // -----------------------------------------------------------
    @BeforeTest
    fun start_koin_context() {
        startKoin {
            allowOverride(true)
            modules(platformTestModules + coreTestModules)
        }
    }

    @AfterTest
    fun stop_koin_context() {
        stopKoin()
    }

    /**
     * Wrapper as solution for accessing a TestScheduler, to be passed
     * into the platformModule. This allows the DatabaseBuilder to
     * establish a single testScheduler via Koin parameters
     * as its queryCoroutineContext for each test.
     *
     * I'm unsure how else to establish a centralized place for
     * ensuring all components of a test belong to the same scheduler
     * and virtual clock.
     */
    fun runTestWrapper(block: suspend TestScope.(BioDao) -> Unit) = runTest {
        val testDispatcher = this.coroutineContext[ContinuationInterceptor.Key] as TestDispatcher

        val writer: TestLogWriter = get()
        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val dao = db.bioDao()

        try {
            this.block(dao)
        } finally {
            writer.reset()
            db.close()
        }
    }

    @Test
    fun should_updateToLatestData_when_newerTimestampProvidedForExistingDay() = runTestWrapper { dao ->
        val dayKey = 20500L // Example epoch day
        val id = "uuid-1"

        // Given: An initial entry for sleep
        val initialContext = DailyContextEntity(
            id = id,
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            hlc = createHlc(1000L),
            lastModified = 1000L
        )
        dao.resolveSync(initialContext)

        // When: A newer HLC arrives (Simulating Cloud Sync)
        val updatedContext = initialContext.copy(
            sleepHours = 8.5,
            hlc = createHlc(1001L),
            lastModified = 1001L
        )
        dao.resolveSync(updatedContext)

        // Then: The higher HLC must win
        val result = dao.getContextByDay(dayKey)
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours)
        assertEquals(createHlc(1001L), result.hlc)

        val allRecords = dao.getAllContexts()
        assertEquals(
            1,
            allRecords.size,
            "Database should contain exactly one record after an resolveSync on the same epochDay"
        )
    }

    @Test
    fun should_ignoreIncomingData_when_timestampIsOlderThanLocal() = runTestWrapper { dao ->
        val dayKey = 20500L
        val id = "uuid-1"

        val existing = DailyContextEntity(
            id = id,
            epochDay = dayKey,
            hlc = createHlc(5000L),
            sleepHours = 8.0,
            lastModified = 5000L // Newer
        )
        dao.resolveSync(existing)

        // When: Stale data arrives from an old sync packet
        val staleIncoming = existing.copy(
            sleepHours = 4.0, // Should be ignored
            lastModified = 2000L // Older
        )
        dao.resolveSync(staleIncoming)

        // Then: Local data remains untouched
        val result = dao.getContextByDay(dayKey)
        assertEquals(8.0, result?.sleepHours)
        assertEquals(createHlc(5000L), result?.hlc)
    }

    @Test
    fun should_returnPartitionedLists_when_databaseContainsMixedNappingStates() = runTestWrapper { dao ->
        // Given:
        val napped = DailyContextEntity(
            id = "uuid-1",
            isNapped = true,
            epochDay = 1L,
            sleepHours = 6.0,
            hlc = createHlc(5000L),
            lastModified = 0L
        )
        val notNapped = DailyContextEntity(
            id = "uuid-2",
            isNapped = false,
            epochDay = 2L,
            sleepHours = 3.0,
            hlc = createHlc(5001L),
            lastModified = 0L
        )

        // When:
        dao.resolveSync(napped)
        dao.resolveSync(notNapped)

        // Then: napped
        val nappedResults = dao.getAllNappedContexts()
        assertEquals(1, nappedResults.size, "Should only find the napped entry.")
        assertEquals(1L, nappedResults[0].epochDay)

        // Then: not napped
        val nonNappedResults = dao.getAllNonNappedContexts()
        assertEquals(1, nonNappedResults.size, "Should only find the non-napped entry.")
        assertEquals(2L, nonNappedResults[0].epochDay)
    }

    @Test
    fun should_returnContextsInDescendingOrder_ignoringDeleted() = runTestWrapper { dao ->
        val days = (1L..3L).map {
            DailyContextEntity(
                id = "id-$it",
                epochDay = it,
                hlc = createHlc(1000L),
                readinessScore = 8,
                sleepHours = 9.0,
                lastModified = 5000L
            )
        }
        days.forEach { dao.resolveSync(it) }

        // Delete the middle day
        dao.markAsDeleted("id-2", createHlc(2000L), 2000L)

        dao.observeAllContexts().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(3L, list[0].epochDay) // Descending
            assertEquals(1L, list[1].epochDay)
        }
    }

    // -----------------------------------------------------------
    // OBSERVATION
    // -----------------------------------------------------------

    @Test
    fun should_emitNewData_when_specificallyObservedDayIsUpdated() = runTestWrapper { dao ->
        val dayKey = 20500L
        val initial = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            sleepHours = 5.0,
            lastModified = 1000L,
            hlc = createHlc(5001L)
        )

        dao.observeContext(dayKey).test {
            // Given: Initially null (or empty)
            assertEquals(null, awaitItem(), "Should start with null if day is not initialized.")

            // When: Data is inserted
            dao.resolveSync(initial)

            assertEquals(5.0, awaitItem()?.sleepHours)

            // When: Updated via sync with newer timestamp
            val update = initial.copy(sleepHours = 9.0, lastModified = 2000L, hlc = createHlc(5002L))
            dao.resolveSync(update)

            // Then:
            assertEquals(9.0, awaitItem()?.sleepHours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_notEmit_when_staleDataIsUpserted() = runTestWrapper { dao ->
        // Given:
        val dayKey = 20500L
        val initial = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            isNapped = true,
            sleepHours = 6.5,
            lastModified = 2000L,
            hlc = createHlc(5001L)
        )

        dao.observeAllContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            dao.resolveSync(initial)
            val currentList = awaitItem()
            assertEquals(1, currentList.size) // Initial emission

            // When: Stale data comes in
            val stale = initial.copy(sleepHours = 99.0, lastModified = 1000L)
            dao.resolveSync(stale)

            // Then:
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_emitUpdatedRecord_when_recordForSameDayIsAmended() = runTestWrapper { dao ->
        // Given:
        val dayKey = 20500L
        val notNappedContext = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            sleepHours = 7.0,
            readinessScore = 7,
            isNapped = false, // Initially, no nap
            lastModified = 1000L,
            hlc = createHlc(5001L)
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.resolveSync(notNappedContext) // Room sees a change and notifies all collectors.
            assertEquals(0, awaitItem().size, "Emission for an empty list expected.")

            val nappedUpdate = notNappedContext.copy(isNapped = true, lastModified = 1001L, hlc = createHlc(ts = 5001, count = 1))
            dao.resolveSync(nappedUpdate)

            // Then:
            val resultList = awaitItem()
            assertEquals(1, resultList.size, "Flow should have emitted a list with a single item.")
            assertEquals(
                true,
                resultList[0].isNapped,
                "Single result from Flow should have 'isNapped' flag as true."
            )

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // What happens when observing a flow, another device comes online and looks
    // to sync a new context for the same day with a later modified date, holding new information.
    @Test
    fun should_emitMergedContext_when_idCollisionOccursWithNewerTimestamp() = runTestWrapper { dao ->
        // Given:
        val dayKey = 20500L
        val initialNapped = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            isNapped = true,
            sleepHours = 6.0,
            lastModified = 1000L,
            hlc = createHlc(5001L)
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.resolveSync(initialNapped)
            val initialNappedEmission = awaitItem()

            val collisionItem = initialNapped.copy(sleepHours = 8.0, lastModified = 1001L, hlc = createHlc(5002L))
            dao.resolveSync(collisionItem)
            val collisionEmission = awaitItem() // there should be a single 'update' emission.

            // Then:
            assertEquals(
                dayKey,
                initialNappedEmission[0].epochDay,
                "Expected first emission of initial napped state."
            )
            assertEquals(
                8.0,
                collisionEmission[0].sleepHours,
                "Expected second emission to have kept updated info."
            )
            assertEquals(
                1,
                collisionEmission.size,
                "Expected second emission to have caused a single update."
            )

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_deterministicallyWin_basedOnNodeId_whenTimestampsMatch() = runTestWrapper { dao ->
        val id = "uuid-1"

        // Node B arrives
        val nodeB = DailyContextEntity(
            id = id,
            hlc = "5000:0:NodeB",
            sleepHours = 2.0,
            epochDay = 2000L,
            readinessScore = 5,
            lastModified = 1000
        )
        dao.resolveSync(nodeB)

        // Node A arrives at the exact same time (Alphabetically 'NodeA' < 'NodeB')
        val nodeA = nodeB.copy(hlc = "5000:0:NodeA")
        dao.resolveSync(nodeA)

        // Then: NodeB should still be the winner because "NodeB" > "NodeA" string-wise
        val result = dao.getContextById(id)
        assertEquals("5000:0:NodeB", result?.hlc)
    }

    // -----------------------------------------------------------
    // DELETION
    // -----------------------------------------------------------

    @Test
    fun should_hideDeletedRecords_fromUiObservables() = runTestWrapper { dao ->
        val dayKey = 20500L
        val entity = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            hlc = createHlc(1000L),
            sleepHours = 5.0,
            readinessScore = 0,
            lastModified = 0L,
            isDeleted = false
        )

        dao.resolveSync(entity)

        dao.observeContext(dayKey).test {
            assertNotNull(awaitItem())

            // When: Logically deleted (Marked as Tombstone)
            dao.markAsDeleted("uuid-1", createHlc(2000L), 2000L)

            // Then: UI Flow should emit null because the record is "gone" to the user
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_maintainTombstone_evenWhenOldDataIsSynced() = runTestWrapper { dao ->
        val id = "uuid-1"
        // Given: A context:
        dao.resolveSync(
            DailyContextEntity(
                id,
                epochDay = 20500L,
                readinessScore = 0,
                sleepHours = 5.0,
                lastModified = 2000L,
                hlc = createHlc(1000L)
            )
        )

        // When: Soft Delete, then old sync packet arrives:
        dao.markAsDeleted(id, createHlc(2000L), 2000L)

        val staleSync = DailyContextEntity(
            id,
            epochDay = 20500L,
            hlc = createHlc(1000L),
            isDeleted = false,
            readinessScore = 0,
            sleepHours = 5.0,
            lastModified = 2000L
        )
        dao.resolveSync(staleSync)

        // Then: The record MUST remain deleted because HLC 2000 > HLC 1000
        val finalRecord = dao.getContextById(id)
        assertEquals(true, finalRecord?.isDeleted, "Tombstone was overwritten by stale data!")
    }

    @Test
    fun should_resurrectRecord_when_newerUpdateFollowsTombstone() = runTestWrapper { dao ->
        val id = "uuid-1"
        val day = 20500L

        dao.upsert(
            DailyContextEntity(
                id = id,
                hlc = createHlc(1000),
                epochDay = day,
                sleepHours = 5.6,
                readinessScore = 5,
                lastModified = 1000L
            )
        )

        // 1. Record is Deleted (HLC 2000)
        dao.markAsDeleted(id, createHlc(2000L), 2000L)

        // 2. A newer update arrives (HLC 3000, isDeleted = false)
        val resurrection = DailyContextEntity(
            id = id, epochDay = day, hlc = createHlc(3000L),
            isDeleted = false, sleepHours = 7.0, lastModified = 3000L
        )
        dao.resolveSync(resurrection)

        // Then: Record must be visible again
        val result = dao.getContextById(id)
        assertEquals(false, result?.isDeleted)
        assertEquals(7.0, result?.sleepHours)
    }

    @Test
    fun should_onlyPruneOldTombstones_leavingRecentOnesIntact() = runTestWrapper { dao ->
        // Given: One old tombstone, one fresh tombstone
        dao.resolveSync(
            DailyContextEntity(
                id = "old",
                epochDay = 1L,
                hlc = createHlc(1000L),
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 1000L
            )
        )
        dao.resolveSync(
            DailyContextEntity(
                id = "new",
                epochDay = 2L,
                hlc = createHlc(5000L),
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 5000L
            )
        )

        // When: Pruning with cutoff at 3000
        dao.hardDeletePruning(3000L)

        // Then:
        assertNull(dao.getContextById("old"), "Old tombstone should be purged")
        assertNotNull(dao.getContextById("new"), "New tombstone must remain for sync dissemination")
    }

    // -----------------------------------------------------------
    // SYNC / RACE CONDITIONS
    // -----------------------------------------------------------

    @Test
    fun should_surviveHlcRaceCondition_withDeterministicWinner() = runTestWrapper { dao ->
        val dayKey = 20500L
        val id = "uuid-global"

        // Given: a flurry of chaotic updates
        val attempts = (1000..2000).map { i ->
            DailyContextEntity(
                id = id,
                epochDay = dayKey,
                sleepHours = i.toDouble(),
                readinessScore = 9,
                hlc = createHlc(i.toLong()),
                lastModified = 1000L
            )
        }

        attempts.shuffled().forEach { incoming ->
            launch { dao.resolveSync(incoming) }
        }
        advanceUntilIdle()

        // Then: The record with the largest HLC (2000) must be the winner
        val result = dao.getContextByDay(dayKey)
        assertEquals(createHlc(2000L), result?.hlc)
        assertEquals(2000.0, result?.sleepHours)
    }

}