package com.mochame.bio.infrastructure

import app.cash.turbine.test
import com.mochame.bio.data.BioMicroSchema
import com.mochame.bio.data.BioMicroSchemaConstructor
import com.mochame.bio.di.BioInfraTestApp
import com.mochame.bio.di.BioTestEnv
import com.mochame.bio.infrastructure.DailyContextCodecV1.Companion.TAG_IS_NAPPED
import com.mochame.bio.infrastructure.DailyContextCodecV1.Companion.TAG_READINESS_SCORE
import com.mochame.bio.infrastructure.DailyContextCodecV1.Companion.TAG_SLEEP_HOURS
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.common.bitmaskOf
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodec.Companion.TAG_CREATED_AT
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodec.Companion.TAG_PRIMARY_KEY
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private inline fun runEnv(
    readyUp: Boolean = true,
    crossinline block: suspend BioTestEnv.(TestScope) -> Unit
) = runPersistenceEnvironment<BioMicroSchema, BioTestEnv>(
    constructor = BioMicroSchemaConstructor,
    koinSetup = { includes(koinConfiguration<BioInfraTestApp>()) },
    block = { testScope ->
        if (readyUp) bootProvider.updateBootState(BootState.Ready)
        block(testScope)
    }
)


class DefaultDailyContextRepositoryTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // UPSERT PIPELINE
    // -----------------------------------------------------------

    @Test
    fun shouldPersistAndRoundtripLosslessTelemetry() = runEnv {
        // August 27, 2026 (00:00:00 UTC)
        val mochaDay = fakeClock.wind()

        val rowId = contextRepo.upsertDay(
            sleepHours = 8.5,
            readinessScore = 90,
            isNapped = true
        )
        // 4am rule
        assertEquals(mochaDay, rowId)

        val fetched = contextDao.getContextById(rowId)
        assertNotNull(fetched)
        assertEquals(mochaDay, fetched.id)
        assertEquals(8.5, fetched.sleepHours)
        assertEquals(90, fetched.readinessScore)
        assertEquals(true, fetched.isNapped)
        assertNotNull(fetched.hlc)
    }

    @Test
    fun shouldIndexByEpochDayAndLinkToIntent() = runEnv {
        val mochaDay = fakeClock.wind()

        contextRepo.upsertDay(
            sleepHours = 7.0,
            readinessScore = 80,
            isNapped = false
        )

        val entity = contextDao.getContextById(mochaDay)
        assertNotNull(entity)
        assertEquals(mochaDay, entity.id)
        assertEquals(false, entity.isDeleted)

        val intents = intentStore.intents
        assertTrue(intents.isNotEmpty())
        val intent = intents.last()
        assertEquals(mochaDay, intent.candidateKey)
        assertEquals(MutationOp.UPSERT, intent.operation)
        assertEquals(
            intent.changedMask,
            bitmaskOf(
                TAG_PRIMARY_KEY,
                TAG_CREATED_AT,
                TAG_SLEEP_HOURS,
                TAG_READINESS_SCORE,
                TAG_IS_NAPPED
            )
        )
    }

    @Test
    fun shouldMergeAndUnsetFieldLevelMutationsWithoutClobbering() = runEnv { // no clobber
        val mochaDay = fakeClock.wind()

        contextRepo.upsertDay(
            sleepHours = 6.5,
            readinessScore = null,
            isNapped = null
        )

        val initialEntity = contextDao.getContextById(mochaDay)
        assertNotNull(initialEntity)
        val initialCreatedAt = initialEntity.createdAt

        contextRepo.upsertDay(
            sleepHours = null,
            readinessScore = 75,
            isNapped = true
        )

        val updatedFetched = contextDao.getContextById(mochaDay)
        assertNotNull(updatedFetched)
        assertEquals(null, updatedFetched.sleepHours)
        assertEquals(75, updatedFetched.readinessScore)
        assertEquals(true, updatedFetched.isNapped)
        assertEquals(initialCreatedAt, updatedFetched.createdAt)
    }

    @Test
    fun shouldSuppressRedundantWrites_on_noopDelta() = runEnv {
        contextRepo.upsertDay(
            sleepHours = 8.0,
            readinessScore = 85,
            isNapped = false
        )

        val initialIntentCount = intentStore.intents.size

        val secondResult = contextRepo.upsertDay(
            sleepHours = 8.0,
            readinessScore = 85,
            isNapped = false
        )

        // Assert: Skipped and no extra intent appended
        assertEquals(0L, secondResult)
        assertEquals(initialIntentCount, intentStore.intents.size)
    }

    // -----------------------------------------------------------
    // DELETION PIPELINE
    // -----------------------------------------------------------

    @Test
    fun shouldSetIsDeleted_on_softDeletionFlow() = runEnv {
        val mochaDay = fakeClock.wind()

        contextRepo.upsertDay(sleepHours = 7.5, readinessScore = 85, isNapped = false)
        val deleteResult = contextRepo.deleteContext(mochaDay)
        assertTrue(deleteResult != 0L)

        // Assert
        val entity = contextDao.getContextById(mochaDay)
        assertNotNull(entity)
        assertTrue(entity.isDeleted)

        val deletionIntent = intentStore.intents.last()
        assertEquals(mochaDay, deletionIntent.candidateKey)
        assertEquals(MutationOp.DELETE, deletionIntent.operation)
    }

    @Test
    fun shouldOmitDeletedRecords_on_uiInvalidationOnDelete() = runEnv {
        val mochaDay = fakeClock.wind()

        contextRepo.upsertDay(sleepHours = 8.0, readinessScore = 90, isNapped = true)

        contextRepo.observeContext(mochaDay).test {
            val initial = awaitItem()
            assertNotNull(initial)
            assertFalse(initial.isDeleted)

            contextRepo.deleteContext(mochaDay)

            val deletedEmission = awaitItem()
            assertNull(deletedEmission)
        }
    }

}