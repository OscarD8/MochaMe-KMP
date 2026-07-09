package com.mochame.bio.test.data.dao

import app.cash.turbine.test
import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.bio.data.BioDao
import com.mochame.bio.data.DailyContextEntity
import com.mochame.bio.test.database.BioMicroSchema
import com.mochame.bio.test.database.BioMicroSchemaConstructor
import com.mochame.bio.test.di.BioDaoTestApp
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.contract.models.HLC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class BaseBioDaoTest : MochaPlatformTest() {

    @Test
    fun should_updateToLatestData_when_newerTimestampProvidedForExistingDay() = runEnv {
        val dayKey = 20500L
        val id = "uuid-1"

        // Given: An initial entry
        val initialContext = DailyContextEntity(
            id = id,
            epochDay = dayKey,
            sleepHours = 6.0,
            readinessScore = 5,
            hlc = createHlc(1000L).toString(), // Refactored: Floor + 1000ms
            lastModified = 1000L
        )
        resolveSync(initialContext)

        // When: A newer HLC arrives
        val updatedContext = initialContext.copy(
            sleepHours = 8.5,
            hlc = createHlc(1001L).toString(), // Refactored
            lastModified = 1001L
        )
        resolveSync(updatedContext)

        // Then: The higher HLC must win
        val result = getContextByDay(dayKey)
        assertNotNull(result)
        assertEquals(8.5, result.sleepHours)
        assertEquals(createHlc(1001L).toString(), result.hlc) // Refactored

        val allRecords = getAllContexts()
        assertEquals(1, allRecords.size)
    }

    @Test
    fun should_ignoreIncomingData_when_timestampIsOlderThanLocal() = runEnv {
        val dayKey = 20500L
        val id = "uuid-1"

        val existing = DailyContextEntity(
            id = id,
            epochDay = dayKey,
            hlc = createHlc(5000L).toString(), // Refactored
            sleepHours = 8.0,
            lastModified = 5000L
        )
        resolveSync(existing)

        // When: Stale data arrives
        val staleIncoming = existing.copy(
            sleepHours = 4.0,
            lastModified = 2000L,
            hlc = createHlc(2000L).toString() // Refactored
        )
        resolveSync(staleIncoming)

        // Then: Local data remains untouched
        val result = getContextByDay(dayKey)
        assertEquals(8.0, result?.sleepHours)
        assertEquals(createHlc(5000L).toString(), result?.hlc) // Refactored[cite: 3]
    }

    @Test
    fun should_returnPartitionedLists_when_databaseContainsMixedNappingStates() = runEnv {
        val napped = DailyContextEntity(
            id = "uuid-1",
            isNapped = true,
            epochDay = 1L,
            sleepHours = 6.0,
            hlc = createHlc(5000L).toString(), // Refactored
            lastModified = 0L
        )
        val notNapped = DailyContextEntity(
            id = "uuid-2",
            isNapped = false,
            epochDay = 2L,
            sleepHours = 3.0,
            hlc = createHlc(5001L).toString(), // Refactored
            lastModified = 0L
        )

        resolveSync(napped)
        resolveSync(notNapped)

        assertEquals(1, getAllNappedContexts().size)
        assertEquals(1, getAllNonNappedContexts().size)
    }

    @Test
    fun should_returnContextsInDescendingOrder_ignoringDeleted() = runEnv {
        val days = (1L..3L).map {
            DailyContextEntity(
                id = "id-$it",
                epochDay = it,
                hlc = createHlc(1000L).toString(), // Refactored
                readinessScore = 8,
                sleepHours = 9.0,
                lastModified = 5000L
            )
        }
        days.forEach { resolveSync(it) }

        // Delete the middle day
        markAsDeleted("id-2", createHlc(2000L).toString(), 2000L) // Refactored

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
            hlc = createHlc(5001L).toString() // Refactored
        )

        observeContext(dayKey).test {
            assertEquals(null, awaitItem())
            resolveSync(initial)
            assertEquals(5.0, awaitItem()?.sleepHours)

            val update = initial.copy(
                sleepHours = 9.0,
                lastModified = 2000L,
                hlc = createHlc(5002L).toString() // Refactored
            )
            resolveSync(update)

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
            isNapped = true,
            sleepHours = 6.5,
            lastModified = 2000L,
            hlc = createHlc(5001L).toString() // Refactored
        )

        observeAllContexts().test {
            assertEquals(0, awaitItem().size)
            resolveSync(initial)
            assertEquals(1, awaitItem().size)

            val stale = initial.copy(
                sleepHours = 99.0,
                lastModified = 1000L,
                hlc = createHlc(1000L).toString() // Refactored
            )
            resolveSync(stale)

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
            isNapped = false,
            lastModified = 1000L,
            hlc = createHlc(5001L).toString() // Refactored
        )

        observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size)
            resolveSync(notNappedContext)
            assertEquals(0, awaitItem().size)

            val nappedUpdate = notNappedContext.copy(
                isNapped = true,
                lastModified = 1001L,
                hlc = createHlc(5001L, 1).toString() // Refactored: Same TS, higher count
            )
            resolveSync(nappedUpdate)

            val resultList = awaitItem()
            assertEquals(1, resultList.size)
            assertEquals(true, resultList[0].isNapped)

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
            isNapped = true,
            sleepHours = 6.0,
            lastModified = 1000L,
            hlc = createHlc(5001L).toString() // Refactored
        )

        observeAllNappedContexts().test {
            assertEquals(0, awaitItem().size)
            resolveSync(initialNapped)
            awaitItem()

            val collisionItem = initialNapped.copy(
                sleepHours = 8.0,
                lastModified = 1001L,
                hlc = createHlc(5002L).toString() // Refactored
            )
            resolveSync(collisionItem)
            val collisionEmission = awaitItem()

            assertEquals(8.0, collisionEmission[0].sleepHours)
            assertEquals(1, collisionEmission.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_deterministicallyWin_basedOnNodeId_whenTimestampsMatch() = runEnv {
        val id = "uuid-1"

        // Node B arrives
        val nodeB = DailyContextEntity(
            id = id,
            hlc = createHlc(5000L, 0, "NodeB").toString(), // Refactored: Deterministic
            sleepHours = 2.0,
            epochDay = 2000L,
            readinessScore = 5,
            lastModified = 1000
        )
        resolveSync(nodeB)

        // Node A arrives at the exact same time
        val nodeA = nodeB.copy(hlc = createHlc(5000L, 0, "NodeA").toString()) // Refactored
        resolveSync(nodeA)

        // Then: NodeB wins because "NodeB" > "NodeA"
        val result = getContextById(id)
        assertEquals(createHlc(5000L, 0, "NodeB").toString(), result?.hlc)
    }

    @Test
    fun should_hideDeletedRecords_fromUiObservables() = runEnv {
        val dayKey = 20500L
        val entity = DailyContextEntity(
            id = "uuid-1",
            epochDay = dayKey,
            hlc = createHlc(1000L).toString(), // Refactored
            sleepHours = 5.0,
            readinessScore = 0,
            lastModified = 0L,
            isDeleted = false
        )

        resolveSync(entity)

        observeContext(dayKey).test {
            assertNotNull(awaitItem())
            markAsDeleted("uuid-1", createHlc(2000L).toString(), 2000L) // Refactored
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_maintainTombstone_evenWhenOldDataIsSynced() = runEnv {
        val id = "uuid-1"
        resolveSync(
            DailyContextEntity(
                id,
                epochDay = 20500L,
                readinessScore = 0,
                sleepHours = 5.0,
                lastModified = 2000L,
                hlc = createHlc(1000L).toString() // Refactored
            )
        )

        markAsDeleted(id, createHlc(2000L).toString(), 2000L) // Refactored

        val staleSync = DailyContextEntity(
            id,
            epochDay = 20500L,
            hlc = createHlc(1000L).toString(), // Refactored: Older than Tombstone
            isDeleted = false,
            readinessScore = 0,
            sleepHours = 5.0,
            lastModified = 2000L
        )
        resolveSync(staleSync)

        val finalRecord = getContextById(id)
        assertEquals(true, finalRecord?.isDeleted)
    }

    @Test
    fun should_resurrectRecord_when_newerUpdateFollowsTombstone() = runEnv {
        val id = "uuid-1"
        val day = 20500L

        upsert(
            DailyContextEntity(
                id = id,
                hlc = createHlc(1000L).toString(), // Refactored
                epochDay = day,
                sleepHours = 5.6,
                readinessScore = 5,
                lastModified = 1000L
            )
        )

        markAsDeleted(id, createHlc(2000L).toString(), 2000L) // Deleted at 2000

        val resurrection = DailyContextEntity(
            id = id, epochDay = day, hlc = createHlc(3000L).toString(), // Refactored: Newer
            isDeleted = false, sleepHours = 7.0, lastModified = 3000L
        )
        resolveSync(resurrection)

        val result = getContextById(id)
        assertEquals(false, result?.isDeleted)
        assertEquals(7.0, result?.sleepHours)
    }

    @Test
    fun should_onlyPruneOldTombstones_leavingRecentOnesIntact() = runEnv {
        resolveSync(
            DailyContextEntity(
                id = "old",
                epochDay = 1L,
                hlc = createHlc(1000L).toString(), // Refactored
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 1000L
            )
        )
        resolveSync(
            DailyContextEntity(
                id = "new",
                epochDay = 2L,
                hlc = createHlc(5000L).toString(), // Refactored
                isDeleted = true,
                sleepHours = 5.0,
                lastModified = 5000L
            )
        )

        hardDeletePruning(3000L)

        assertNull(getContextById("old"))
        assertNotNull(getContextById("new"))
    }

    @Test
    fun should_copyDataCorrectly_when_multipleEventsOccurOnSameDay() = runEnv { scope ->
        val dayKey = 20500L
        val id = "uuid-global"

        val attempts = (1000..1010).map { i ->
            DailyContextEntity(
                id = id,
                epochDay = dayKey,
                sleepHours = i.toDouble(),
                readinessScore = 9,
                hlc = createHlc(i.toLong()).toString(),
                lastModified = 1000L
            )
        }

        attempts.shuffled().forEach { incoming ->
            scope.launch { resolveSync(incoming) }
        }
        scope.advanceUntilIdle()

        val result = getContextByDay(dayKey)
        assertEquals(createHlc(1010L).toString(), result?.hlc)
        assertEquals(1010.0, result?.sleepHours)
    }

    /**
     * Factory function for generating deterministic HLCs in tests.
     */
    private fun createHlc(
        offsetMs: Long = 0,
        count: Int = 0,
        nodeId: String = "test-node"
    ): HLC = HLC(
        ts = HLC.APP_RELEASE_MS + offsetMs,
        count = count,
        nodeId = nodeId
    )
}