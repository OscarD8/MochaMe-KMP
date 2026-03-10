package com.mochame.app.database

import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

abstract class SharedBioDaoTest {

    // Defer database creation to the platform-specific subclasses
    protected abstract fun createDatabase(): MochaDatabase

    @Test
    fun testBioInsertion() = runTest {
        // Arrange
        val db = createDatabase()
        val dao = db.bioDao()

        // Act
        dao.upsertDailyContext(
            context = DailyContextEntity(
                id = "1",
                epochDay = 25000L,
                sleepHours = 5.4,
                readinessScore = 2,
                lastModified = 1
            )
        )
        val retrievedItem = dao.getContextByEpochDaySync(25000L)

        // Assert
        assertNotNull(retrievedItem)
    }
}