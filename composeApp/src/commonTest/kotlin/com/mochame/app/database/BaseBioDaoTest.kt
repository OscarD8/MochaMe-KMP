package com.mochame.app.database

import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.inject
import org.koin.core.module.Module
import org.koin.test.KoinTest
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
    fun verify_upsert_replaces_existing_context_for_same_day() = runTest {
        val dayKey = 20500L // Example epoch day

        // Given: An initial entry for sleep
        val initialContext = DailyContextEntity(
            id = "uuid-initial",
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            lastModified = 1000L
        )
        dao.insertOrReplace(initialContext)

        // When: The user updates their sleep (The "Double Wake-up")
        val updatedContext = initialContext.copy(
            id = "uuid-updated", // Even with a different UUID, epochDay is the Unique Index
            sleepHours = 8.5,
            readinessScore = 9
        )
        dao.insertOrReplace(updatedContext)

        // Then: The database should only have ONE record for that day with updated values
        val result = dao.getContextByDay(dayKey)
        println("DEBUG: Fetched DailyContext for day $dayKey: $result")
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours, "Updated context values did not update.")
        assertEquals(9, result.readinessScore, "Updated context values did not update.")

        val allRecords = dao.getAllContexts()
        assertEquals(1, allRecords.size, "Database should contain exactly one record after an upsert on the same epochDay")
    }

    @Test
    fun verify_multiple_days_exist_as_separate_entities() = runTest {
        // Given: Two different biological days
        val monday = DailyContextEntity("id-1", 20500L, 7.0, 6, 1000L)
        val tuesday = DailyContextEntity("id-2", 20501L, 8.0, 6, 1000L)

        // When: Both are saved
        dao.insertOrReplace(monday)
        dao.insertOrReplace(tuesday)

        // Then: Integrity check
        val all = dao.getAllContexts()
        assertEquals(2, all.size)
    }
}