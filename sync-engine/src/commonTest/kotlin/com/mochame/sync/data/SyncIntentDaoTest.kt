package com.mochame.sync.data

import com.mochame.support.MochaPlatformTest
import com.mochame.support.TestHlcFactory
import com.mochame.support.runPersistenceEnvironment
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.di.data.SyncPersistenceTestApp
import com.mochame.sync.fakes.createTestIntentEntity
import com.mochame.sync.schema.SyncMicroSchema
import com.mochame.sync.schema.SyncMicroSchemaConstructor
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend SyncIntentDao.(TestScope) -> Unit) =
    runPersistenceEnvironment<SyncMicroSchema, SyncIntentDao>(
        constructor = SyncMicroSchemaConstructor,
        koinSetup = { includes(koinConfiguration<SyncPersistenceTestApp>()) },
        block = block,
    )


private const val testId = "test-batch"


class SyncIntentDaoTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // BATCH ALLOCATION
    // -----------------------------------------------------------
    @Test
    fun should_claimBatchInStrictChronologicalOrder_when_insertedOutSequence() = runEnv {
        // Given
        val (hlc1, hlc2, hlc3) = TestHlcFactory.chronologicalSequence(size = 3)

        // Intentionally upserting out of order to verify database index sorting
        val intentLater =
            createTestIntentEntity(hlc = hlc3, candidateKey = "key-3")
        val intentEarlier =
            createTestIntentEntity(hlc = hlc1, candidateKey = "key-1")
        val intentMiddle =
            createTestIntentEntity(hlc = hlc2, candidateKey = "key-2")

        upsert(intentLater)
        upsert(intentEarlier)
        upsert(intentMiddle)

        // When
        val rowsClaimed = claimBatch(id = testId, limit = 3)
        val claimedBatch = getClaimedBatch(id = testId)

        // Then
        assertEquals(3, rowsClaimed)
        assertEquals(3, claimedBatch.size)

        // Causal alignment validation is completely type-safe now
        assertEquals(hlc1.toString(), claimedBatch[0].hlc)
        assertEquals(hlc2.toString(), claimedBatch[1].hlc)
        assertEquals(hlc3.toString(), claimedBatch[2].hlc)
    }

    @Test
    fun should_limitClaimedBatchSize_when_backlogExceedsLimit() = runEnv {
        // Given unordered sequence of intents against causal HLC's
        val hlcs = TestHlcFactory.chronologicalSequence(size = 5)
        val entities = hlcs.mapIndexed { index, hlc ->
            createTestIntentEntity(
                hlc = hlc,
                candidateKey = "key-$index"
            )
        }
        entities.shuffled().forEach { entity ->
            upsert(entity)
        }

        val maxBatchLimit = 3
        val nextTestId = "next-session-id"

        // When
        val rowsAffected = claimBatch(id = testId, limit = maxBatchLimit)
        val claimedBatch = getClaimedBatch(id = testId)

        // Then
        assertEquals(maxBatchLimit, rowsAffected)
        assertEquals(maxBatchLimit, claimedBatch.size)

        assertEquals(hlcs[0].toString(), claimedBatch[0].hlc)
        assertEquals(hlcs[1].toString(), claimedBatch[1].hlc)
        assertEquals(hlcs[2].toString(), claimedBatch[2].hlc)

        // When - Fetch the next batch from the remaining backlog
        val nextRowsAffected = claimBatch(id = nextTestId, limit = maxBatchLimit)
        val nextClaimedBatch = getClaimedBatch(id = nextTestId)

        // Then - Verify it captures the remainder of the items chronologically
        assertEquals(2, nextRowsAffected)
        assertEquals(2, nextClaimedBatch.size)

        assertEquals(hlcs[3].toString(), nextClaimedBatch[0].hlc)
        assertEquals(hlcs[4].toString(), nextClaimedBatch[1].hlc)
    }

    @Test
    fun should_isolateDataBatches_when_multipleSessionsClaimSequentially() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 4)
        hlcs.forEachIndexed { index, hlc ->
            upsert(
                createTestIntentEntity(
                    hlc = hlc,
                    candidateKey = "key-$index"
                )
            )
        }

        val sessionAlpha = "session-alpha"
        val sessionBeta = "session-beta"

        // When
        val rowsAlpha = claimBatch(id = sessionAlpha, limit = 2)
        val batchAlpha = getClaimedBatch(id = sessionAlpha)

        val rowsBeta = claimBatch(id = sessionBeta, limit = 2)
        val batchBeta = getClaimedBatch(id = sessionBeta)

        // Then
        // Verify Alpha
        assertEquals(2, rowsAlpha)
        assertEquals(2, batchAlpha.size)
        assertEquals(hlcs[0].toString(), batchAlpha[0].hlc)
        assertEquals(hlcs[1].toString(), batchAlpha[1].hlc)

        // Verify Beta
        assertEquals(2, rowsBeta)
        assertEquals(2, batchBeta.size)
        assertEquals(hlcs[2].toString(), batchBeta[0].hlc)
        assertEquals(hlcs[3].toString(), batchBeta[1].hlc)

        // Confirm isolation
        val alphaHlcs = batchAlpha.map { it.hlc }.toSet()
        val betaHlcs = batchBeta.map { it.hlc }.toSet()
        val intersection = alphaHlcs.intersect(betaHlcs)

        assertTrue(
            intersection.isEmpty(),
            "Data leakage detected: Sessions contains overlapping HLC records"
        )
    }

    @Test
    fun should_skipQuarantinedIntents_when_claimingFreshBatch() = runEnv {
        // Given
        val (hlcOldestQuarantined, hlcNewerPending) = TestHlcFactory.chronologicalSequence(
            size = 2
        )

        val quarantinedIntent = createTestIntentEntity(
            hlc = hlcOldestQuarantined,
            candidateKey = "bad-key",
            status = SyncStatus.QUARANTINED
        )

        val healthyIntent =
            createTestIntentEntity(
                hlc = hlcNewerPending,
                candidateKey = "good-key",
                status = SyncStatus.PENDING
            )

        upsert(quarantinedIntent)
        upsert(healthyIntent)

        // When
        val rowsClaimed = claimBatch(id = testId, limit = 1)
        val claimedBatch = getClaimedBatch(id = testId)

        // Then
        assertEquals(1, rowsClaimed)
        assertEquals(1, claimedBatch.size)
        assertEquals(hlcNewerPending.toString(), claimedBatch[0].hlc)
        assertEquals("good-key", claimedBatch[0].candidateKey)
    }

    @Test
    fun should_returnZeroAffectedRows_when_backlogIsEmptyOrFullyLeased() = runEnv {
        // Scenario A
        // Given nada data
        val rowsAlpha = claimBatch(id = testId, limit = 10)
        val batchAlpha = getClaimedBatch(id = testId)

        assertEquals(0, rowsAlpha)
        assertTrue(batchAlpha.isEmpty())

        // Scenario B: Session Beta
        // Given items exist, but they are already completely locked under another session
        val targetHlc = TestHlcFactory.create()
        val activelyLeasedIntent =
            createTestIntentEntity(
                hlc = targetHlc,
                candidateKey = "locked-key",
                status = SyncStatus.SYNCING,
                syncId = "session-active-owner"
            )

        upsert(activelyLeasedIntent)

        val sessionBeta = "session-contender"

        // When
        val rowsBeta = claimBatch(id = sessionBeta, limit = 10)
        val batchBeta = getClaimedBatch(id = sessionBeta)

        // Then
        assertEquals(0, rowsBeta)
        assertTrue(batchBeta.isEmpty())
    }

    @Test
    fun should_filterCorrectRowsOnly_when_ledgerContainsMixedStatuses() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 4)

        val intentSuccess = createTestIntentEntity(
            hlc = hlcs[0],
            candidateKey = "key-1",
            status = SyncStatus.SUCCESS
        )
        val intentLeased = createTestIntentEntity(
            hlc = hlcs[1],
            candidateKey = "key-2",
            status = SyncStatus.SYNCING,
            syncId = "other-id"
        )
        val intentPending = createTestIntentEntity(
            hlc = hlcs[2],
            candidateKey = "key-3",
            status = SyncStatus.PENDING
        )
        val intentQuarantined = createTestIntentEntity(
            hlc = hlcs[3],
            candidateKey = "key-4",
            status = SyncStatus.QUARANTINED
        )

        upsert(intentSuccess)
        upsert(intentLeased)
        upsert(intentPending)
        upsert(intentQuarantined)

        val currentSession = "session-extractor"

        // When
        val rowsClaimed = claimBatch(id = currentSession, limit = 10)
        val claimedBatch = getClaimedBatch(id = currentSession)

        // Then
        assertEquals(1, rowsClaimed)
        assertEquals(1, claimedBatch.size)
        assertEquals(hlcs[2].toString(), claimedBatch[0].hlc)
        assertEquals("key-3", claimedBatch[0].candidateKey)
    }


    // -----------------------------------------------------------
    // STATE MANAGEMENT
    // -----------------------------------------------------------
    @Test
    fun should_stampDiagnosticMessageAcrossBatch_when_batchFailureOccurs() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 3)
        val entity1 = createTestIntentEntity(hlc = hlcs[0])
        val entity2 = createTestIntentEntity(hlc = hlcs[1])
        val entity3 = createTestIntentEntity(hlc = hlcs[2])

        upsert(entity1)
        upsert(entity2)
        upsert(entity3)

        val targetHlcStrings = listOf(hlcs[0].toString(), hlcs[2].toString())
        val failureMessage = "HTTP 502: Bad Gateway Gateway Timeout"

        // When
        // Stamping errors explicitly across a subset of the batch
        stampLastError(hlcs = targetHlcStrings, message = failureMessage)
        // Lock the session to pull them back out for verification
        claimBatch(id = "verification-session", limit = 10)
        val updatedEntities = getClaimedBatch(id = "verification-session")

        // Then
        val record1 = updatedEntities.find { it.hlc == hlcs[0].toString() }
            ?: throw AssertionError()
        val record2 = updatedEntities.find { it.hlc == hlcs[1].toString() }
            ?: throw AssertionError()
        val record3 = updatedEntities.find { it.hlc == hlcs[2].toString() }
            ?: throw AssertionError()

        assertEquals(failureMessage, record1.lastErrorMessage)
        assertNull(record2.lastErrorMessage)
        assertEquals(failureMessage, record3.lastErrorMessage)
    }

    @Test
    fun should_releaseAllActiveSessionLocks_when_crashRecoveryTriggered() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 3)

        // Two records simulate being caught mid-upload during a power failure/crash
        val strandedRecord1 = createTestIntentEntity(
            hlc = hlcs[0],
            status = SyncStatus.SYNCING,
            syncId = "crash-session-1"
        )
        val strandedRecord2 = createTestIntentEntity(
            hlc = hlcs[1],
            status = SyncStatus.SYNCING,
            syncId = "crash-session-1"
        )
        // One healthy record that was untouched
        val untouchedRecord = createTestIntentEntity(
            hlc = hlcs[2],
            status = SyncStatus.PENDING,
            syncId = null
        )

        upsert(strandedRecord1)
        upsert(strandedRecord2)
        upsert(untouchedRecord)

        // When
        val rowsRecovered = clearAllLocksAndResetToPending()

        // Then
        assertEquals(2, rowsRecovered)

        // Verify a completely fresh session can seamlessly claim the entire backlog now
        val nextSessionId = "fresh-start-worker"
        val totalClaimedCount = claimBatch(id = nextSessionId, limit = 10)
        val currentActiveBatch = getClaimedBatch(id = nextSessionId)

        assertEquals(3, totalClaimedCount)
        currentActiveBatch.forEach { entity ->
            assertEquals(SyncStatus.SYNCING, entity.syncStatus)
            assertEquals(nextSessionId, entity.syncId)
            assertNull(entity.lastErrorMessage)
        }
    }

    @Test
    fun should_accuratelyTrackBlobExistence_when_payloadIsOverflowed() = runEnv {
        // Given
        val targetBlobId = "blob-large-payload-789"
        val hlc = TestHlcFactory.create()

        val intentWithBlob =
            createTestIntentEntity(hlc = hlc, overflowBlobId = targetBlobId)

        assertFalse(existsByBlobId(targetBlobId))

        // When
        upsert(intentWithBlob)

        // Then
        assertTrue(existsByBlobId(targetBlobId))
        assertFalse(existsByBlobId("non-existent-blob-id"))
    }

    @Test
    fun should_resetLeaseState_when_resetLeaseInvoked() = runEnv {
        // Given
        val hlc = TestHlcFactory.create()
        val initialActiveLease = createTestIntentEntity(
            hlc = hlc,
            status = SyncStatus.SYNCING,
            syncId = "active-session-123"
        )

        upsert(initialActiveLease)

        // Then
        val initialClaim = claimBatch(id = "verification-session", limit = 1)
        assertEquals(0, initialClaim)

        // When
        // Business logic telling the DAO to reset the lease and bump retries to 1
        resetLease(hlc = hlc, retryCount = 1)

        // Then
        // Rows should be claimable by a new batch
        val rowsClaimed = claimBatch(id = "verification-session", limit = 1)
        val updatedEntities = getClaimedBatch(id = "verification-session")

        assertEquals(1, rowsClaimed)
        val verifiedRecord = updatedEntities.first()
        assertEquals(1, verifiedRecord.retryCount)
        assertEquals(
            SyncStatus.SYNCING,
            verifiedRecord.syncStatus
        ) // Flipped back to syncing via the new claim
        assertEquals(
            "verification-session",
            verifiedRecord.syncId
        )   // Carries the new owner ID
    }

    @Test
    fun should_returnStaleLeasedIntents_when_leasedAtIsBeforeCutoff() = runEnv {
        // Given
        val hlcs = TestHlcFactory.chronologicalSequence(size = 2)
        val cutoffTime = TestHlcFactory.BASE_TEST_TIME
        val targetBlobId = "stale-blob-marker"

        // Row whose lease has expired (leasedAt is older than the cutoff)
        val staleLease = createTestIntentEntity(
            hlc = hlcs[0],
            overflowBlobId = targetBlobId,
            leasedAt = cutoffTime - 1000L,
            status = SyncStatus.SYNCING,
            syncId = "dead-session"
        )

        // Row whose lease is fresh and active (leasedAt is newer than the cutoff)
        val activeLease = createTestIntentEntity(
            hlc = hlcs[1],
            status = SyncStatus.SYNCING,
            syncId = "live-session",
            leasedAt = cutoffTime + 1000L
        )

        upsert(staleLease)
        upsert(activeLease)

        // When
        val staleRecords = getStaleLeasedIntents(cutoffTime)

        // Then
        assertEquals(1, staleRecords.size)
        assertEquals(targetBlobId, staleRecords.first().overflowBlobId)
    }

    // -----------------------------------------------------------
    // MAINTENANCE
    // -----------------------------------------------------------
    @Test
    fun should_deleteSuccessRecord_when_olderThanCutoff() = runEnv {
        // Given
        val hlc = TestHlcFactory.create()
        val cutoffTime = TestHlcFactory.BASE_TEST_TIME
        val targetBlobId = "blob-to-delete"

        val oldSuccess = createTestIntentEntity(
            hlc = hlc,
            status = SyncStatus.SUCCESS,
            overflowBlobId = targetBlobId,
            createdAt = cutoffTime - 1000L // 1 second older than cutoff
        )

        upsert(oldSuccess)

        // When
        val rowsDeleted = pruneOldSynced(cutoffTime, limit = 10)

        // Then
        assertEquals(1, rowsDeleted)
        assertFalse(existsByBlobId(targetBlobId))
    }

    @Test
    fun should_keepSuccessRecord_when_newerThanCutoff() = runEnv {
        // Given
        val hlc = TestHlcFactory.create()
        val cutoffTime = TestHlcFactory.BASE_TEST_TIME
        val targetBlobId = "blob-to-keep"

        val recentSuccess = createTestIntentEntity(
            hlc = hlc,
            status = SyncStatus.SUCCESS,
            overflowBlobId = targetBlobId,
            createdAt = cutoffTime + 1000L // 1 second newer than cutoff
        )

        upsert(recentSuccess)

        // When
        val rowsDeleted = pruneOldSynced(cutoffTime, limit = 10)

        // Then
        assertEquals(0, rowsDeleted)
        assertTrue(existsByBlobId(targetBlobId))
    }

    @Test
    fun should_keepPendingRecord_when_olderThanCutoff() = runEnv {
        // Given
        val hlc = TestHlcFactory.create()
        val cutoffTime = TestHlcFactory.BASE_TEST_TIME
        val targetBlobId = "blob-pending-safety"

        val oldPending = createTestIntentEntity(
            hlc = hlc,
            status = SyncStatus.PENDING,
            overflowBlobId = targetBlobId,
            createdAt = cutoffTime - 1000L // Chronologically old, but un-synced
        )

        upsert(oldPending)

        // When
        val rowsDeleted = pruneOldSynced(cutoffTime, limit = 10)

        // Then
        assertEquals(0, rowsDeleted)
        assertTrue(existsByBlobId(targetBlobId))
    }

}