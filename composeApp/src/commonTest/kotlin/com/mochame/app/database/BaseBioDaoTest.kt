package com.mochame.app.database

import app.cash.turbine.test
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.entity.DailyContextEntity
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


abstract class BaseBioDaoTest : KoinTest {
    // Each platform (Species) provides its own Room/SQLite configuration
    abstract val platformTestModule: Module

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
        // 1. Create the dispatcher tied to THIS test's scheduler
        // val roomDispatcher = StandardTestDispatcher(testScheduler)
        val testDispatcher = this.coroutineContext[ContinuationInterceptor] as TestDispatcher

        // 2. Start Koin specifically for this test run
        startKoin {
            modules(platformTestModule)
        }

        // 3. Request the DB and PASS this dispatcher as a parameter
        // .setQueryCoroutineContext(testDispatcher) for each platform
        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val dao = db.bioDao()

        try {
            this.block(dao)
        } finally {
            db.close()
            stopKoin()
        }
    }

    @Test
    fun should_updateToLatestData_when_newerTimestampProvidedForExistingDay() = runTestWrapper { dao ->
        val dayKey = 20500L // Example epoch day

        // Given: An initial entry for sleep
        val initialContext = DailyContextEntity(
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            lastModified = 1000L
        )
        dao.resolveSync(initialContext)

        // When: The user updates their sleep (The "Double Wake-up")
        val updatedContext = initialContext.copy(
            sleepHours = 8.5,
            readinessScore = 9,
            lastModified = 1001L
        )
        dao.resolveSync(updatedContext)

        // Then: The database should only have ONE record for that day with updated values
        val result = dao.getContext(dayKey)
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours, "Updated context values did not update.")
        assertEquals(9, result.readinessScore, "Updated context values did not update.")

        val allRecords = dao.getAllContexts()
        assertEquals(1, allRecords.size, "Database should contain exactly one record after an resolveSync on the same epochDay")
    }

    @Test
    fun should_ignoreIncomingData_when_timestampIsOlderThanLocal() = runTestWrapper { dao ->
        val dayKey = 20500L
        val existing = DailyContextEntity(
            epochDay = dayKey,
            sleepHours = 8.0,
            lastModified = 5000L // Newer
        )
        dao.resolveSync(existing)

        val staleIncoming = existing.copy(
            sleepHours = 4.0, // Should be ignored
            lastModified = 2000L // Older
        )
        dao.resolveSync(staleIncoming)

        val result = dao.getContext(dayKey)
        assertEquals(8.0, result?.sleepHours, "Database should have kept the newer local data.")
        assertEquals( dayKey, result?.epochDay, "Database should have kept the original ID.")
    }

    @Test
    fun should_returnPartitionedLists_when_databaseContainsMixedNappingStates() = runTestWrapper { dao ->
        // Given:
        val napped = DailyContextEntity(
            isNapped = true,
            epochDay = 1L,
            sleepHours = 6.0,
            lastModified = 0L
        )
        val notNapped = DailyContextEntity(
            isNapped = false,
            epochDay = 2L,
            sleepHours = 3.0,
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
    fun should_returnContextsInDescendingOrder_when_fetchingAll() = runTestWrapper { dao ->
        // Given: Three separate days
        val day1 = DailyContextEntity(epochDay = 1L, sleepHours = 3.0, lastModified = 1000L)
        val day2 = DailyContextEntity(epochDay = 2L, sleepHours = 2.0, lastModified = 1000L)
        val day3 = DailyContextEntity(epochDay = 3L, sleepHours = 8.0, lastModified = 1000L)

        dao.resolveSync(day1)
        dao.resolveSync(day3)
        dao.resolveSync(day2)

        // When: Fetching all history
        val results = dao.getAllContexts()

        // Then: The order should be 3, 2, 1 (DESC)
        assertEquals(3, results.size, "Expected three contexts.")
        assertEquals(3L, results[0].epochDay, "Expected correct ordering of contexts")
        assertEquals(2L, results[1].epochDay, "Expected correct ordering of contexts")
        assertEquals(1L, results[2].epochDay, "Expected correct ordering of contexts")
    }

    // -----------------------------------------------------------
    // OBSERVATION
    // -----------------------------------------------------------

    @Test
    fun should_emitNewData_when_specificallyObservedDayIsUpdated() = runTestWrapper { dao ->
        val dayKey = 20500L
        val initial = DailyContextEntity(epochDay = dayKey, sleepHours = 5.0, lastModified = 1000L)

        dao.observeContext(dayKey).test {
            // Given: Initially null (or empty)
            assertEquals(null, awaitItem(), "Should start with null if day is not initialized.")

            // When: Data is inserted
            dao.resolveSync(initial)
            assertEquals(5.0, awaitItem()?.sleepHours)

            // When: Updated via sync with newer timestamp
            val update = initial.copy(sleepHours = 9.0, lastModified = 2000L)
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
        val initial = DailyContextEntity(epochDay = dayKey, isNapped = true, sleepHours = 6.5, lastModified = 2000L)

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
            epochDay = dayKey,
            sleepHours = 7.0,
            readinessScore = 7,
            isNapped = false, // Initially, no nap
            lastModified = 1000L
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.resolveSync(notNappedContext) // Room sees a change and notifies all collectors.
            assertEquals(0, awaitItem().size, "Emission for an empty list expected.")

            val nappedUpdate = notNappedContext.copy(isNapped = true, lastModified = 1001L)
            dao.resolveSync(nappedUpdate)

            // Then:
            val resultList = awaitItem()
            assertEquals(1, resultList.size, "Flow should have emitted a list with a single item.")
            assertEquals(true, resultList[0].isNapped, "Single result from Flow should have 'isNapped' flag as true.")

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
            epochDay = dayKey,
            isNapped = true,
            sleepHours = 6.0,
            lastModified = 1000L
        )

        dao.observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Flow should start empty.")

            // When:
            dao.resolveSync(initialNapped)
            val initialNappedEmission = awaitItem()

            val collisionItem = initialNapped.copy(sleepHours = 8.0, lastModified = 1001L)
            dao.resolveSync(collisionItem)
            val collisionEmission = awaitItem() // there should be a single 'update' emission.

            // Then:
            assertEquals(dayKey, initialNappedEmission[0].epochDay, "Expected first emission of initial napped state.")
            assertEquals(8.0, collisionEmission[0].sleepHours, "Expected second emission to have kept updated info.")
            assertEquals(1, collisionEmission.size, "Expected second emission to have caused a single update.")

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -----------------------------------------------------------
    // DELETION
    // -----------------------------------------------------------

    @Test
    fun should_removeContext_when_deleteContextByIdIsCalled() = runTestWrapper { dao ->
        // Given: A day exists
        val dayKey = 20500L
        val entity = DailyContextEntity(epochDay = dayKey, sleepHours = 7.0, lastModified = 1000L)
        dao.resolveSync(entity)

        // When: We clear the day
        dao.deleteContext(dayKey)

        // Then: The record should be gone
        val result = dao.getContext(dayKey)
        assertEquals(null, result, "Record should have been deleted from the database.")
    }

    // -----------------------------------------------------------
    // SYNC
    // -----------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun should_maintainIntegrity_when_multipleDevicesSyncSimultaneously() = runTestWrapper { dao ->
        val dayKey = 20500L
        val actualLastModified = 200000L
        val actualSleepHours = 9.0
        val finalState = DailyContextEntity(dayKey, actualSleepHours, 0, actualLastModified)

        // Given: A flurry of concurrent sync attempts with varying timestamps
        val syncAttempts = (1..1000).map { i ->
            DailyContextEntity(dayKey, sleepHours = 8.0, lastModified = i.toLong())
        } + finalState

        // When: They all rush the DAO at once (simulating multi-device sync)
        syncAttempts.shuffled().forEach { incoming ->
            launch { dao.resolveSync(incoming) }
        }
        advanceUntilIdle()

        // Then: Only the one with the highest timestamp should remain
        val result = dao.getContext(dayKey)
        assertEquals(actualLastModified, result?.lastModified, "The highest timestamp did not win the race.")
        assertEquals(9.0, result?.sleepHours)
    }
    
}