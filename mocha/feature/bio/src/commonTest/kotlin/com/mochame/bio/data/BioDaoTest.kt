package com.mochame.bio.data

import app.cash.turbine.test
import com.mochame.bio.di.BioDaoTestApp
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.common.TriState
import com.mochame.utils.fixtures.HlcTestFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend BioDao.(TestScope) -> Unit) =
    runPersistenceEnvironment<BioMicroSchema, BioDao>(
        constructor = BioMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<BioDaoTestApp>()) },
        block = block
    )


@ExperimentalCoroutinesApi
class BioDaoTest : MochaPlatformTest() {

    @Test
    fun should_updateToLatestData_when_newerTimestampProvidedForExistingDay() = runEnv {
        val dayKey = 20500L
        val id = "uuid-1"

        val initialContext = DailyContextEntity(
            id = id,
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            hlc = HlcTestFactory.create(1000L).toString(),
            lastModified = 1000L
        )
        upsert(initialContext)

        val updatedContext = initialContext.copy(
            sleepHours = 8.5,
            hlc = HlcTestFactory.create(1001L).toString(),
            lastModified = 1001L
        )
        upsert(updatedContext)

        val result = getContextByDay(dayKey)
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours)
        assertEquals(HlcTestFactory.create(1001L).toString(), result.hlc)

        val allRecords = getAllContexts()
        assertEquals(1, allRecords.size)
    }

    @Test
    fun should_returnPartitionedLists_when_databaseContainsMixedNappingStates() = runEnv {
        val napped = DailyContextEntity(
            id = "uuid-1",
            isNapped = TriState.TRUE,
            epochDay = 1L,
            sleepHours = 6.0,
            hlc = HlcTestFactory.create(5000L).toString(),
            lastModified = 0L
        )
        val notNapped = DailyContextEntity(
            id = "uuid-2",
            isNapped = TriState.FALSE,
            epochDay = 2L,
            sleepHours = 3.0,
            hlc = HlcTestFactory.create(5001L).toString(),
            lastModified = 0L
        )

        upsert(napped)
        upsert(notNapped)

        assertEquals(1, getAllNappedContexts().size)
        assertEquals(1, getAllNonNappedContexts().size)
    }

    @Test
    fun should_returnContextsInDescendingOrder_ignoringDeleted() = runEnv {
        val days = (1L..3L).map {
            DailyContextEntity(
                id = "id-$it",
                epochDay = it,
                hlc = HlcTestFactory.create(1000L).toString(),
                readinessScore = 8,
                sleepHours = 9.0,
                lastModified = 5000L
            )
        }
        days.forEach { upsert(it) }

        markAsDeleted("id-2", HlcTestFactory.create(2000L).toString(), 2000L)

        observeAllContexts().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(3L, list[0].epochDay)
            assertEquals(1L, list[1].epochDay)
        }
    }

    @Test
    fun should_emitNewData_when_specificallyObservedDayIsUpdated() = runEnv {
        val dayKey = 20500L
        val initial = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            sleepHours = 5.0,
            lastModified = 1000L,
            hlc = HlcTestFactory.create(5001L).toString()
        )

        observeContext(dayKey).test {
            assertEquals(null, awaitItem())
            upsert(initial)
            assertEquals(5.0, awaitItem()?.sleepHours)

            val update = initial.copy(
                sleepHours = 9.0,
                lastModified = 2000L,
                hlc = HlcTestFactory.create(5002L).toString()
            )
            upsert(update)

            assertEquals(9.0, awaitItem()?.sleepHours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_notEmit_when_staleDataIsUpserted() = runEnv {
        val dayKey = 20500L
        val initial = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            isNapped = TriState.TRUE,
            sleepHours = 6.5,
            lastModified = 2000L,
            hlc = HlcTestFactory.create(5001L).toString()
        )

        observeAllContexts().test {
            assertEquals(0, awaitItem().size)
            upsert(initial)
            assertEquals(1, awaitItem().size)

            val stale = initial.copy(
                sleepHours = 99.0,
                lastModified = 1000L,
                hlc = HlcTestFactory.create(1000L).toString()
            )
            upsert(stale)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_emitUpdatedRecord_when_recordForSameDayIsAmended() = runEnv {
        val dayKey = 20500L
        val notNappedContext = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            sleepHours = 7.0,
            readinessScore = 7,
            isNapped = TriState.FALSE,
            lastModified = 1000L,
            hlc = HlcTestFactory.create(5001L).toString()
        )

        observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size, "Failed on start")
            upsert(notNappedContext)
            assertEquals(0, awaitItem().size, "Failed after single upsert")

            val nappedUpdate = notNappedContext.copy(
                isNapped = TriState.TRUE,
                lastModified = 1001L,
                hlc = HlcTestFactory.create(5001L, count = 1).toString()
            )
            upsert(nappedUpdate)

            val resultList = awaitItem()
            assertEquals(1, resultList.size)
            assertEquals(TriState.TRUE, resultList[0].isNapped)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_emitMergedContext_when_idCollisionOccursWithNewerTimestamp() = runEnv {
        val dayKey = 20500L
        val initialNapped = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            isNapped = TriState.TRUE,
            sleepHours = 6.0,
            lastModified = 1000L,
            hlc = HlcTestFactory.create(5001L).toString()
        )

        observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size)
            upsert(initialNapped)
            awaitItem()

            val collisionItem = initialNapped.copy(
                sleepHours = 8.0,
                lastModified = 1001L,
                hlc = HlcTestFactory.create(5002L).toString()
            )
            upsert(collisionItem)
            val collisionEmission = awaitItem()

            assertEquals(8.0, collisionEmission[0].sleepHours)
            assertEquals(1, collisionEmission.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_hideDeletedRecords_fromUiObservables() = runEnv {
        val dayKey = 20500L
        val entity = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            hlc = HlcTestFactory.create(1000L).toString(),
            sleepHours = 5.0,
            readinessScore = 0,
            lastModified = 0L,
            isDeleted = false
        )

        upsert(entity)

        observeContext(dayKey).test {
            assertNotNull(awaitItem())
            markAsDeleted("uuid-1", HlcTestFactory.create(2000L).toString(), 2000L)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_resurrectRecord_when_newerUpdateFollowsTombstone() = runEnv {
        val id = "uuid-1"
        val day = 20500L

        upsert(
            DailyContextEntity(
                id = id,
                hlc = HlcTestFactory.create(1000L).toString(),
                epochDay = day,
                sleepHours = 5.6,
                readinessScore = 5,
                lastModified = 1000L
            )
        )

        markAsDeleted(id, HlcTestFactory.create(2000L).toString(), 2000L)

        val resurrection = DailyContextEntity(
            id = id, epochDay = day, hlc = HlcTestFactory.create(3000L).toString(),
            isDeleted = false, sleepHours = 7.0, lastModified = 3000L
        )
        upsert(resurrection)

        val result = getContextById(id)
        assertEquals(false, result?.isDeleted)
        assertEquals(7.0, result?.sleepHours)
    }

    @Test
    fun should_onlyPruneOldTombstones_leavingRecentOnesIntact() = runEnv {
        upsert(
            DailyContextEntity(
                id = "old",
                epochDay = 1L,
                hlc = HlcTestFactory.create(1000L).toString(),
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 1000L
            )
        )
        upsert(
            DailyContextEntity(
                id = "new",
                epochDay = 2L,
                hlc = HlcTestFactory.create(5000L).toString(),
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 5000L
            )
        )

        hardDeletePruning(3000L)

        assertNull(getContextById("old"))
        assertNotNull(getContextById("new"))
    }

}