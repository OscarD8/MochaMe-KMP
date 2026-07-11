package com.mochame.sync.infrastructure.stores

import app.cash.turbine.test
import com.mochame.support.MochaPlatformTest
import com.mochame.support.TestHlcFactory
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.di.data.PersistenceEnv
import com.mochame.sync.di.data.SyncPersistenceTestApp
import com.mochame.sync.fakes.createTestIntentEntity
import com.mochame.sync.fakes.createTestSyncIntent
import com.mochame.sync.schema.SyncMicroSchema
import com.mochame.sync.schema.SyncMicroSchemaConstructor
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend PersistenceEnv.(TestScope) -> Unit) =
    runPersistenceEnvironment<SyncMicroSchema, PersistenceEnv>(
        constructor = SyncMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<SyncPersistenceTestApp>()) },
        block = block
    )


class SyncIntentStoreTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // INBOUND / OUTBOUND INTEGRITY
    // -----------------------------------------------------------
    @Test
    fun should_preserveExactDataFields_when_mappingToEntityAndBackToDomain() = runEnv {
        // Given
        val hlc = TestHlcFactory.create()
        val inlinePayload =
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val candidateKey = "test-store-key-123"

        val originalIntent = createTestSyncIntent(
            hlc = hlc,
            candidateKey = candidateKey,
            payload = inlinePayload
        )

        // When
        intentStore.recordIntent(originalIntent)
        val retrievedIntent = intentStore.getPendingByPrimaryKey(candidateKey)

        // Then
        assertNotNull(retrievedIntent)

        assertEquals(originalIntent.hlc, retrievedIntent.hlc)
        assertEquals(
            originalIntent.featureSchemaVersion,
            retrievedIntent.featureSchemaVersion
        )
        assertEquals(originalIntent.candidateKey, retrievedIntent.candidateKey)
        assertEquals(originalIntent.module, retrievedIntent.module)
        assertEquals(originalIntent.model, retrievedIntent.model)
        assertEquals(originalIntent.operation, retrievedIntent.operation)
        assertEquals(originalIntent.syncStatus, retrievedIntent.syncStatus)
        assertEquals(originalIntent.createdAt, retrievedIntent.createdAt)

        // Validate nullability preservation
        assertNull(retrievedIntent.syncId)
        assertNull(retrievedIntent.overflowBlobId)
        assertNull(retrievedIntent.leasedAt)
        assertNull(retrievedIntent.lastErrorMessage)
        assertNull(retrievedIntent.diagnosticSummary)

        // Verify payload byte consistency
        assertNotNull(retrievedIntent.payload)
        assertTrue(inlinePayload.contentEquals(retrievedIntent.payload!!))
    }

    // -----------------------------------------------------------
    // COLLECTIONS / EMISSIONS
    // -----------------------------------------------------------
    @Test
    fun should_maintainCollectionSizeAndOrdering_when_retrievingClaimedBatch() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 3)
        val sessionId = "store-batch-session"

        // Unordered seeding of the database via the DAO to isolate the store's retrieval mapper
        intentDao.upsert(createTestIntentEntity(hlc = hlcs[2], candidateKey = "key-2"))
        intentDao.upsert(createTestIntentEntity(hlc = hlcs[0], candidateKey = "key-0"))
        intentDao.upsert(createTestIntentEntity(hlc = hlcs[1], candidateKey = "key-1"))

        // When
        val rowsClaimed = intentStore.claimBatch(id = sessionId, limit = 10)
        val claimedDomainBatch = intentStore.getClaimedBatch(id = sessionId)

        // Then
        assertEquals(3, rowsClaimed)
        assertEquals(3, claimedDomainBatch.size)

        // Confirm Chronology
        assertEquals(hlcs[0], claimedDomainBatch[0].hlc)
        assertEquals(hlcs[1], claimedDomainBatch[1].hlc)
        assertEquals(hlcs[2], claimedDomainBatch[2].hlc)

        assertEquals("key-0", claimedDomainBatch[0].candidateKey)
        assertEquals("key-1", claimedDomainBatch[1].candidateKey)
        assertEquals("key-2", claimedDomainBatch[2].candidateKey)
    }

    @Test
    fun should_returnEmptyList_when_noPendingIntentsExistForModule() = runEnv {
        // Given
        val targetModule = "empty-feature-module"

        // When
        val result = intentStore.getPendingByModule(targetModule)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty(), "Expected an empty list, but received elements!")
    }

    @Test
    fun should_emitUpdatedSummary_when_intentIsQuarantined() = runEnv {
        // Given
        val targetModule = FeatureContext.Type.UNRECOGNIZED_FALLBACK
        val hlc = TestHlcFactory.create()

        // When
        intentStore.observeQuarantinedCountByModule().test {
            val initialItem = awaitItem()
            assertTrue(initialItem.isEmpty())

            val quarantinedEntity = createTestIntentEntity(
                hlc = hlc,
                status = SyncStatus.QUARANTINED
            )
            intentDao.upsert(quarantinedEntity)

            // Then
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)

            val summary = updatedList.first()
            assertEquals(targetModule, summary.module)
            assertEquals(1, summary.count)

            cancelAndIgnoreRemainingEvents()
        }
    }

}

