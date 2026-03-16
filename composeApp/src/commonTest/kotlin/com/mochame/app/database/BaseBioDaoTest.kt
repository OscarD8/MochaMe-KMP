package com.mochame.app.database

import app.cash.turbine.test
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


abstract class BaseBioDaoTest : KoinTest {
    // Each platform (Species) provides its own Room/SQLite configuration
    abstract val platformTestModule: Module

    private val db: MochaDatabase by inject()
    private val dao: BioDao by inject()

    @BeforeTest
    fun setup() {
        startKoin {
            modules(platformTestModule)
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun should_updateToLatestData_when_newerTimestampProvidedForExistingDay() = runTest {
        val dayKey = 20500L // Example epoch day

        // Given: An initial entry for sleep
        val initialContext = DailyContextEntity(
            id = "uuid-initial",
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            lastModified = 1000L
        )
        dao.upsertSync(initialContext)

        // When: The user updates their sleep (The "Double Wake-up")
        val updatedContext = initialContext.copy(
            id = "uuid-updated", // Even with a different UUID, epochDay is the Unique Index
            sleepHours = 8.5,
            readinessScore = 9,
            lastModified = 1001L
        )
        dao.upsertSync(updatedContext)

        // Then: The database should only have ONE record for that day with updated values
        val result = dao.getContextByDay(dayKey)
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours, "Updated context values did not update.")
        assertEquals(9, result.readinessScore, "Updated context values did not update.")
        assertEquals("uuid-initial", result.id, "Updated context values did not update.")

        val allRecords = dao.getAllContexts()
        assertEquals(1, allRecords.size, "Database should contain exactly one record after an upsert on the same epochDay")
    }

    @Test
    fun should_persistSeparateEntities_when_multipleDaysAreUpserted() = runTest {
        // Given: Two different biological days
        val monday = DailyContextEntity( "id-1",20500L, 7.0, 6, 1000L)
        val tuesday = DailyContextEntity( "id-2",20501L, 8.0, 6, 1000L)

        // When: Both are saved
        dao.upsertSync(monday)
        dao.upsertSync(tuesday)

        // Then: Integrity check
        val all = dao.getAllContexts()
        assertEquals(2, all.size)
    }

    @Test
    fun should_ignoreIncomingData_when_timestampIsOlderThanLocal() = runTest {
        val dayKey = 20500L
        val existing = DailyContextEntity(
            id = "uuid-local",
            epochDay = dayKey,
            sleepHours = 8.0,
            lastModified = 5000L // Newer
        )
        dao.upsertSync(existing)

        val staleIncoming = existing.copy(
            id = "uuid-remote",
            sleepHours = 4.0, // Should be ignored
            lastModified = 2000L // Older
        )
        dao.upsertSync(staleIncoming)

        val result = dao.getContextByDay(dayKey)
        assertEquals(8.0, result?.sleepHours, "Database should have kept the newer local data.")
        assertEquals("uuid-local", result?.id, "Database should have kept the original ID.")
    }

    @Test
    fun should_returnPartitionedLists_when_databaseContainsMixedNappingStates() = runTest {
        // Given:
        val napped = DailyContextEntity(
            id = "1",
            isNapped = true,
            epochDay = 1L,
            sleepHours = 6.0,
            lastModified = 0L
        )
        val notNapped = DailyContextEntity(
            id = "2",
            isNapped = false,
            epochDay = 2L,
            sleepHours = 3.0,
            lastModified = 0L
        )

        // When:
        dao.upsertSync(napped)
        dao.upsertSync(notNapped)

        // Then: napped
        val nappedResults = dao.getAllNappedContexts()
        assertEquals(1, nappedResults.size, "Should only find the napped entry.")
        assertEquals("1", nappedResults[0].id)

        // Then: not napped
        val nonNappedResults = dao.getAllNonNappedContexts()
        assertEquals(1, nonNappedResults.size, "Should only find the non-napped entry.")
        assertEquals("2", nonNappedResults[0].id)
    }

    // -----------------------------------------------------------
    // DAILY CONTEXT FLOWS
    // -----------------------------------------------------------

    @Test
    fun should_notEmit_when_staleDataIsUpserted() = runTest {
        // Given:
        val dayKey = 20500L
        val initial = DailyContextEntity(id = "u1", epochDay = dayKey, isNapped = true, sleepHours = 6.5, lastModified = 2000L)

        dao.observeAllContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            dao.upsertSync(initial)
            val initialEmission = awaitItem()
            assertEquals(1, initialEmission.size) // Initial emission

            // When: Stale data comes in
            val stale = initial.copy(sleepHours = 99.0, lastModified = 1000L)
            dao.upsertSync(stale)

            // Then:
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_emitUpdatedRecord_when_recordWithSameIdIsAmended() = runTest {
        // Given:
        val dayKey = 20500L
        val notNappedContext = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            sleepHours = 7.0,
            readinessScore = 7,
            isNapped = false, // Initially, no nap
            lastModified = 1000L
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.upsertSync(notNappedContext) // Room sees a change and notifies all collectors.
            assertEquals(0, awaitItem().size, "Emission for an empty list expected.")

            val nappedUpdate = notNappedContext.copy(isNapped = true, lastModified = 1001L)
            dao.upsertSync(nappedUpdate)

            // Then:
            val resultList = awaitItem()
            assertEquals(1, resultList.size, "Flow should have emitted a list with a single item.")
            assertEquals(true, resultList[0].isNapped, "Single result from Flow should have 'isNapped' flag as true.")

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
            // cancel the reamining events becaseu th
        }
    }

    // What happens when observing a flow, another device comes online and looks
    // to sync a new context for the same day with a later modified date, holding new information.
    @Test
    fun should_emitMergedContext_when_idCollisionOccursWithNewerTimestamp() = runTest {
        // Given:
        val dayKey = 20500L
        val initialNapped = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            isNapped = true,
            sleepHours = 6.0,
            lastModified = 1000L
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.upsertSync(initialNapped)
            val initialNappedEmission = awaitItem()

            val collisionItem = initialNapped.copy(id = "uuid-2", sleepHours = 8.0, lastModified = 1001L)
            dao.upsertSync(collisionItem)
            val collisionEmission = awaitItem() // there should be a single 'update' emission.

            // Then:
            assertEquals("uuid-1", initialNappedEmission[0].id, "Expected first emission of initial napped state.")
            assertEquals("uuid-1", collisionEmission[0].id, "Expected second emission to have adopted id of first state.")
            assertEquals(8.0, collisionEmission[0].sleepHours, "Expected second emission to have kept updated info.")
            assertEquals(1, collisionEmission.size, "Expected second emission to have caused a single update.")

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

}