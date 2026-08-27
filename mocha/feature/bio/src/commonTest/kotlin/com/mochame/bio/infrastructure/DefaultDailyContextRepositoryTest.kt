package com.mochame.bio.infrastructure

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
import kotlin.test.assertNotNull
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
        val baseDay = 20692L
        val mochaDay = 20691L
        fakeClock.setTime(Instant.fromEpochSeconds(baseDay * 86_400L))

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
        val baseDay = 20693L
        val mochaDay = 20692L
        fakeClock.setTime(Instant.fromEpochSeconds(baseDay * 86_400L))

        // Act
        contextRepo.upsertDay(
            sleepHours = 7.0,
            readinessScore = 80,
            isNapped = false
        )

        // Assert
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

}