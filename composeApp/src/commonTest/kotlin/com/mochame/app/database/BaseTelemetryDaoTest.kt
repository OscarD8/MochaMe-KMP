package com.mochame.app.database

import app.cash.turbine.test
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.database.dao.SyncTombstoneDao
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.database.entity.DomainEntity
import com.mochame.app.database.entity.TopicEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertTrue
import kotlin.time.Clock

abstract class BaseTelemetryDaoTest : KoinTest {

    abstract val platformTestModule: Module

    fun runTestWrapper(block: suspend TestScope.(
        TelemetryDao, SyncTombstoneDao) -> Unit
    ) = runTest {
        // 1. Create the dispatcher tied to THIS test's scheduler
        // val roomDispatcher = StandardTestDispatcher(testScheduler)
        // I have now tried to unify to a single dispatcher... unsure if necessary
        val testDispatcher = this.coroutineContext[ContinuationInterceptor]

        // 2. Start Koin specifically for this test run
        startKoin {
            modules(platformTestModule)
        }

        // 3. Request the DB and PASS this dispatcher as a parameter
        // .setQueryCoroutineContext(testDispatcher) for each platform
        val db: MochaDatabase = get { parametersOf(testDispatcher) }
        val telemetryDao = db.telemetryDao()
        val syncTombstoneDao = db.syncTombstoneDao()

        try {
            this.block(telemetryDao, syncTombstoneDao)
        } finally {
            db.close()
            stopKoin()
        }
    }

    // -----------------------------------------------------------
    // SYNC & DELETION
    // -----------------------------------------------------------
    @Test
    fun should_GenerateTombstone_When_DomainIsHardDeleted() = runTestWrapper { telemetryDao, syncTombstoneDao ->
        // Given: a domain
        val testId = "uuid-to-kill"
        val newDomain = DomainEntity(testId, "Work", "#000", "ic", true, 0L)
        telemetryDao.upsertDomain(newDomain)

        // When: domain deleted
        telemetryDao.hardDeleteDomain(newDomain.id)

        // Then: trigger fires and valid tombstone is created
        val tombstone = syncTombstoneDao.getAllDomainDeletions().first()

        assertNotNull(tombstone, message = "Tombstone not created on deletion of a domain.")
        assertEquals(testId, tombstone.entityId)
        assertEquals("domains", tombstone.tableName)
    }

    @Test
    fun should_GenerateMultipleTombstones_When_DomainIsHardDeleted() = runTestWrapper { telemetryDao, syncTombstoneDao ->
        // Given: a domain holding a topic
        val domainId = "domain-xyz"
        val topicId = "topic-abc"
        val now = Clock.System.now().toEpochMilliseconds()

        telemetryDao.upsertDomain(
            DomainEntity(domainId, "Work", "#6F4E37", "ic_work", true, now)
        )
        telemetryDao.upsertTopic(
            TopicEntity(topicId, domainId, "Kotlin KMP", true, now)
        )

        // When: a domain deletion occurs during flow observation
        syncTombstoneDao.observeRecentDeletions(since = 0L).test {
            assertEquals(0, awaitItem().size)

            telemetryDao.hardDeleteDomain(domainId)

            // Then: triggers cascade, and a single emission for two accurate tombstones
            val tombstones = awaitItem()
            val ids = tombstones.map { it.entityId }
            val tables = tombstones.map { it.tableName }

            assertTrue(ids.contains(domainId), "Missing Domain tombstone")
            assertTrue(ids.contains(topicId), "Missing Topic tombstone (Cascade failed)")
            assertTrue(tables.contains("domains"), "Missing Domain tombstone data")
            assertTrue(tables.contains("topics"), "Missing Topic tombstone data (Cascade failed)")

            cancelAndIgnoreRemainingEvents()
        }
    }

}