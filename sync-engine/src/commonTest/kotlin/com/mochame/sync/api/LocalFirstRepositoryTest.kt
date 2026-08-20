@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.api

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.node.fixtures.di.FixturesNodeConfig
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.di.api.LocalFirstRepoTestApp
import com.mochame.sync.di.api.LocalFirstRepoTestEnv
import com.mochame.sync.internal.fixtures.serialization.FakeFeatureCodec
import com.mochame.sync.internal.fixtures.serialization.FeatureEntity
import com.mochame.sync.internal.fixtures.serialization.deriveContext
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.utils.fixtures.TestHlcFactory
import com.mochame.utils.fixtures.TestNodeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private inline fun runEnv(crossinline block: suspend LocalFirstRepoTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<LocalFirstRepoTestEnv>(
        koinSetup = { includes(koinConfiguration<LocalFirstRepoTestApp>()) },
        block = block
    )

@ExperimentalCoroutinesApi
class LocalFirstRepositoryTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // BOOT STATE GUARD
    // -----------------------------------------------------------

    @Test
    fun awaitReady_onCriticalFailureWithException_unwrapsAndThrowsException() =
        runEnv {
            val rootCause = IllegalStateException("Corrupt local database")
            bootProvider.updateBootState(
                BootState.CriticalFailure(
                    message = "DB_CORRUPT",
                    exception = rootCause
                )
            )

            val thrown = assertFailsWith<IllegalStateException> {
                repo.delete(5L)
            }

            assertEquals("Corrupt local database", thrown.message)
        }

    @Test
    fun awaitReady_whenInitializingOrIdle_suspendsUntilTransitionToActive() = runEnv {
        hlcFactory.hydrate(null, TestNodeId.A)
        bootProvider.updateBootState(BootState.Initializing)
        repo.seed(FeatureEntity(id = 1L))

        var completed = false
        val deferred = it.async {
            repo.delete(1L) // suspending
            completed = true
        }

        it.runCurrent()
        assertEquals(false, completed)
        assertEquals(false, deferred.isCompleted)

        bootProvider.updateBootState(BootState.Idle)
        it.runCurrent()
        assertEquals(false, completed)

        bootProvider.updateBootState(BootState.Ready)
        it.runCurrent()

        assertTrue(completed)
        assertTrue(deferred.isCompleted)
    }

    @Test
    fun awaitReady_whenRemainingInitializing_timesOutAndThrows() = runEnv {
        bootProvider.updateBootState(BootState.Initializing)

        val error = assertFailsWith<MochaException.Persistent.BootInitializationError> {
            repo.delete(1L)
        }

        assertContains(error.message, "timed out")
        assertEquals(
            FixturesNodeConfig.BOOT_TIMEOUT.inWholeMilliseconds,
            it.testScheduler.currentTime
        )
    }

    @Test
    fun awaitReady_whenReady_returnsImmediately() = runEnv {
        setupValidContext()

        repo.upsert(1L) { FeatureEntity() }

        assertEquals(0L, it.testScheduler.currentTime)
    }

    // -----------------------------------------------------------
    // LOCAL PIPELINE
    // -----------------------------------------------------------

    // FAKING SERIALIZATION / FIELD DIFFING ----------------------

    @Test
    fun localUpsert_onNewEntity_stampsHlc_recordsPendingIntent_andNotifiesWorker() = runEnv {
        setupValidContext()
        val candidateKey = 101L

        val result =
            repo.upsert(candidateKey) { FeatureEntity(id = candidateKey, textValue = "NEW_ITEM") }

        assertEquals(101L, result)

        // Verify entity state in repository
        val stored = repo.storedEntities[candidateKey]
        assertNotNull(stored, "Stored entity")
        assertEquals("NEW_ITEM", stored.textValue, "Text value")
        assertEquals(1, hlcFactory.getNextHlcCallCount, "HLC call count")
        assertEquals(stored.hlc, nodeManager.getMaxHlc(), "Max HLC")

        // Verify SyncIntent record
        val recordedIntents = intentStore.intents
        assertEquals(1, recordedIntents.size, "Intent count")
        val intent = recordedIntents.first()
        assertEquals(candidateKey, intent.candidateKey, "Candidate key")
        assertEquals(MutationOp.UPSERT, intent.operation, "Operation")
        assertEquals(SyncStatus.PENDING, intent.syncStatus, "Sync status")
        assertEquals(stored.hlc, intent.hlc, "Intent HLC")
        assertNotNull(intent.payload, "Payload")
        assertEquals(null, intent.overflowBlobId, "Overflow blob ID")

        // Verify dispatched worker post commit
        assertEquals(1, workerHook.invalidationCount, "Invalidation Hook")
    }

    @Test
    fun localDelete_onExistingActiveEntity_recordsDeletionIntent_andPersists() = runEnv {
        setupValidContext()
        val candidateKey = 104L
        val initialHlc = hlcFactory.getNextHlc()
        val activeEntity = FeatureEntity(
            id = candidateKey,
            hlc = initialHlc,
            isDeleted = false,
            textValue = "TO_BE_DELETED"
        )
        repo.seed(activeEntity)

        val result = repo.delete(candidateKey)

        assertEquals(104L, result)

        val stored = repo.storedEntities[candidateKey]
        assertNotNull(stored)
        assertTrue(stored.isDeleted)

        val recordedIntents = intentStore.intents
        assertEquals(1, recordedIntents.size)
        val intent = recordedIntents.first()
        assertEquals(MutationOp.DELETE, intent.operation)
        assertEquals(SyncStatus.PENDING, intent.syncStatus)
        assertEquals(1, workerHook.invalidationCount)
    }

    @Test
    fun localDelete_onNonExistentRecord_throwsPersistentStateIssue() = runEnv {
        setupValidContext()
        val nonExistentKey = 999L

        val exception = assertFailsWith<MochaException.Persistent.StateIssue> {
            repo.delete(nonExistentKey)
        }

        assertEquals(
            exception.message.contains("Local Delete attempt against non-existent record: $nonExistentKey"),
            true
        )
        assertEquals(0, intentStore.intents.size)
        assertEquals(0, workerHook.invalidationCount)
        assertEquals(0, hlcFactory.getNextHlcCallCount)
    }

    @Test
    fun localDelete_onAlreadyDeletedEntity_skipsWithoutErrorOrIntentGeneration() = runEnv {
        setupValidContext()
        val candidateKey = 105L
        val initialHlc = hlcFactory.getNextHlc()
        val deletedEntity = FeatureEntity(
            id = candidateKey,
            hlc = initialHlc,
            isDeleted = true,
            textValue = "ALREADY_DELETED"
        )
        repo.seed(deletedEntity)
        val initialHlcCalls = hlcFactory.getNextHlcCallCount

        val result = repo.delete(candidateKey)

        assertEquals(0L, result)
        assertEquals(0, intentStore.intents.size)
        assertEquals(0, workerHook.invalidationCount)
        assertEquals(initialHlcCalls, hlcFactory.getNextHlcCallCount)
    }

    @Test
    fun processRemoteIntent_whenIncomingDeleteHasOlderHlcThanLocalActiveEntity_skipsAndPreservesActiveState() =
        runEnv {
            setupValidContext()
            val candidateKey = 110L

            // Device A: Local active entity with a newer HLC
            val localHlc = TestHlcFactory.createWithOffset((-1).minutes)
            val localEntity = FeatureEntity(
                id = candidateKey,
                hlc = localHlc,
                isDeleted = false,
                textValue = "LOCAL_ACTIVE_TEXT",
                countValue = 42
            )
            repo.seed(localEntity)

            // Device B: Obsolete remote delete
            val obsoleteDeleteHlc = TestHlcFactory.createWithOffset((-5).minutes)
            val decodeContext = DecodeContext(
                candidateKey = candidateKey,
                hlc = obsoleteDeleteHlc,
                op = MutationOp.DELETE,
                featureSchemaVersion = 1
            )

            // Device A: Ingest remote intent
            repo.processRemoteIntent(decodeContext, FakeFeatureCodec.BYTES_PRESET)

            // Final State Assertions
            val stored = repo.storedEntities[candidateKey]
            assertNotNull(stored, "Stored entity must still exist")
            assertFalse(stored.isDeleted, "Entity remains active; obsolete delete is rejected")
            assertEquals("LOCAL_ACTIVE_TEXT", stored.textValue)
            assertEquals(42, stored.countValue)
            assertEquals(localHlc, stored.hlc, "Entity HLC is not modified")
            assertTrue(writer.logs.any { "Obsolete Remote Delete" in it.message })

            // 5. Side-effect Assertions
            assertEquals(0, intentStore.intents.size, "No intents recorded")
            assertEquals(0, workerHook.invalidationCount, "No worker invalidation")
        }

    @Test
    fun processRemoteIntent_whenIncomingDeleteTargetsAlreadyDeletedEntityWithNewerHlc_skipsAndPreservesTombstone() =
        runEnv {
            setupValidContext()
            val candidateKey = 111L

            // Device A: Deleted entity with an older HLC
            val localHlc = TestHlcFactory.createWithOffset((-5).minutes)
            val localTombstone = FeatureEntity(
                id = candidateKey,
                hlc = localHlc,
                isDeleted = true,
                textValue = "ALREADY_DELETED_TEXT",
                countValue = 0
            )
            repo.seed(localTombstone)

            // Incoming remote delete intent with a newer HLC
            val remoteNewerHlc = TestHlcFactory.createWithOffset((-1).minutes)
            val decodeContext = DecodeContext(
                candidateKey = candidateKey,
                hlc = remoteNewerHlc,
                op = MutationOp.DELETE,
                featureSchemaVersion = 1,
            )

            // Device A: Ingest remote intent
            repo.processRemoteIntent(decodeContext, FakeFeatureCodec.BYTES_PRESET)

            // Final State Assertions
            val stored = repo.storedEntities[candidateKey]
            assertNotNull(stored, "Stored entity must still exist as tombstone")
            assertTrue(stored.isDeleted, "Entity remains deleted")
            assertEquals("ALREADY_DELETED_TEXT", stored.textValue)
            assertEquals(0, stored.countValue)
            assertEquals(localHlc, stored.hlc, "Local tombstone HLC remains unchanged")
            assertTrue(writer.logs.any { "Local record is already deleted" in it.message })

            // Side-effect Assertions
            assertEquals(0, intentStore.intents.size, "No intents recorded for skipped")
            assertEquals(0, workerHook.invalidationCount, "No worker invalidation triggered")
        }

    @Test
    fun processRemoteIntent_whenExistingIsNullAndOpIsDelete_skipsGracefullyWithoutRecordingIntent() =
        runEnv {
            setupValidContext()
            val candidateKey = 112L

            // Device B: Remote delete intent for record only present in local state
            val remoteHlc = TestHlcFactory.createWithOffset((-1).minutes)
            val decodeContext = DecodeContext(
                candidateKey = candidateKey,
                hlc = remoteHlc,
                op = MutationOp.DELETE,
                featureSchemaVersion = 1
            )

            // Device A: ingesting remote intent
            repo.processRemoteIntent(decodeContext, FakeFeatureCodec.BYTES_PRESET)

            // Final State Assertions
            val stored = repo.storedEntities[candidateKey]
            assertEquals(null, stored, "No entity should be created or stored")
            assertTrue(writer.logs.any { "Non-existent local record" in it.message })
            // Side-effect Assertions
            assertEquals(0, intentStore.intents.size, "No intents recorded")
            assertEquals(0, workerHook.invalidationCount, "No worker invalidation triggered")
        }

    // INTEGRATED SERIALIZATION / FIELD DIFFING ------------------

    @Test
    fun localUpsert_onExistingEntity_computesFieldDiff_andStampsUpdatedFieldHlcs() = runEnv {
        setupValidContext()
        val integratedRepo = createCodecIntegratedRepo()

        val candidateKey = 102L
        val initialHlc = hlcFactory.getNextHlc()
        val initialEntity = FeatureEntity(
            id = candidateKey,
            hlc = initialHlc,
            textValue = "ORIGINAL",
            countValue = 10
        )
        integratedRepo.seed(initialEntity)

        val result = integratedRepo.upsert(candidateKey) { existing ->
            existing!!.copy(textValue = "UPDATED") // diff encodes proto tag 4
        }

        assertEquals(102L, result)

        val updated = integratedRepo.storedEntities[candidateKey]
        assertNotNull(updated)
        assertEquals("UPDATED", updated.textValue)
        assertEquals(10, updated.countValue)
        assertTrue(updated.hlc > initialHlc)
        assertNotEquals(updated.fieldHlcs, initialEntity.fieldHlcs)
        assertTrue(writer.logs.any { "OP:UPSERT [4]" in it.message })

        val recordedIntents = intentStore.intents
        assertEquals(1, recordedIntents.size)
        assertEquals(MutationOp.UPSERT, recordedIntents.first().operation)
        assertEquals(1, workerHook.invalidationCount)
    }

    @Test
    fun localUpsert_whenUnchanged_returnsViaOnSkipAfterTriggeringFieldDiffing_withoutRecordingIntentOrUpdatingHlcFields() =
        runEnv {
            setupValidContext()
            val integratedRepo = createCodecIntegratedRepo()

            val candidateKey = 103L
            val initialHlc = hlcFactory.getNextHlc()
            val initialEntity = FeatureEntity(
                id = candidateKey,
                hlc = initialHlc,
                textValue = "UNCHANGED",
                countValue = 5
            )
            integratedRepo.seed(initialEntity)

            // localUpsert computes exact same state -> codec returns null payload diff
            val result = integratedRepo.upsert(candidateKey) { initialEntity }

            assertEquals(0L, result)
            assertTrue(writer.logs.any { "skipping" in it.message })
            assertEquals(0, intentStore.intents.size)
            assertEquals(0, workerHook.invalidationCount)
            assertEquals(2, hlcFactory.getNextHlcCallCount)
        }

    @Test
    fun localUpsert_onDeletedEntity_restoresDeletion_andEmitsRestoreIntent() = runEnv {
        val integratedRepo = createCodecIntegratedRepo()
        val candidateKey = 106L
        val initialHlc = TestHlcFactory.createWithOffset(offset = (-1).minutes)
        setupValidContext()

        // Initial State & Deletion
        val initialEntity = FeatureEntity(
            id = candidateKey,
            hlc = initialHlc,
            isDeleted = false,
            textValue = "INITIAL_STATE",
            countValue = 0
        )
        integratedRepo.seed(initialEntity)
        integratedRepo.delete(candidateKey)

        val deletedEntity = integratedRepo.storedEntities[candidateKey]
        assertNotNull(deletedEntity, "Deleted state must exist prior to restore")
        assertTrue(deletedEntity.isDeleted, "Entity is deleted")

        // Restoration via localUpsert
        val result = integratedRepo.upsert(candidateKey) { existing ->
            existing!!.copy(
                isDeleted = false,
                textValue = "RESTORED_STATE",
                countValue = 1
            )
        }
        assertEquals(candidateKey, result)

        // Verify restored entity
        val stored = integratedRepo.storedEntities[candidateKey]
        assertNotNull(stored, "Stored entity exists after restore")
        assertEquals(false, stored.isDeleted, "Deletion status is restored")
        assertEquals("RESTORED_STATE", stored.textValue)
        assertEquals(1, stored.countValue)
        assertTrue(stored.hlc > deletedEntity.hlc, "HLC to be updated")
        assertNotEquals(deletedEntity.fieldHlcs, stored.fieldHlcs, "Field HLCs must be updated")

        // Verify SyncIntent generation
        assertEquals(2, intentStore.intents.size, "Contains delete and subsequent restore intents")
        val restoreIntent = intentStore.intents.last()
        assertEquals(MutationOp.UPSERT, restoreIntent.operation)
        assertEquals(SyncStatus.PENDING, restoreIntent.syncStatus)
        assertEquals(stored.hlc, restoreIntent.hlc)
        assertNotNull(restoreIntent.payload, "Payload contains encoded restore delta")

        // Verify side-effects
        assertEquals(2, workerHook.invalidationCount, "Invalidation triggered twice")
    }

    @Test
    fun processRemoteIntent_whenLocalDeleteIsMoreRecentThanIncomingUpsert_preservesDeletionWhileMergingFieldValues() =
        runEnv {
            val integratedRepo = createCodecIntegratedRepo()
            val candidateKey = 107L
            val deletionHlc = TestHlcFactory.createWithOffset((-1).minutes)
            setupValidContext()

            // Device A: local delete
            val initialEntity = FeatureEntity(
                id = candidateKey,
                hlc = deletionHlc,
                isDeleted = false,
                textValue = "INITIAL_DELETED_TEXT",
                countValue = 10
            )
            integratedRepo.seed(initialEntity)
            integratedRepo.delete(candidateKey)

            // Device B: remote upsert
            val remoteState = FeatureEntity(
                id = candidateKey,
                hlc = TestHlcFactory.createWithOffset((-2).minutes),
                textValue = "REMOTE_UPDATED_TEXT",
                countValue = 10
            )
            val payload = integratedCodec.encode(remoteState, null)
            val decodeContext = remoteState.deriveContext()
            assertNotNull(payload, "Payload before processRemoteIntent")

            // Device A: Ingest remote intent
            integratedRepo.processRemoteIntent(decodeContext, payload)
            val finalEntity = integratedRepo.storedEntities[candidateKey]
            assertNotNull(finalEntity, "Stored after processRemoteIntent")

            // Verify final state
            assertTrue(finalEntity.isDeleted, "Deletion status preserved")
            assertEquals("REMOTE_UPDATED_TEXT", finalEntity.textValue)
            assertEquals(initialEntity.countValue, finalEntity.countValue)
            assertNotEquals(initialEntity.textValue, finalEntity.textValue)
            assertNotEquals(initialEntity.fieldHlcs, finalEntity.fieldHlcs)

            // Verify side-effects
            assertEquals(1, intentStore.intents.size, "Only local deletion.")
            assertEquals(1, workerHook.invalidationCount, "Only local deletion.")
        }

    @Test
    fun processRemoteIntent_whenIncomingUpsertIsMoreRecentThanDelete_shouldRestoreEntityAndApplyFieldDiff() =
        runEnv {
            val integratedRepo = createCodecIntegratedRepo()
            val candidateKey = 108L
            val initialHlc = TestHlcFactory.create()
            setupValidContext(initialHlc)

            // Device A: Seed active entity and perform local delete
            val initialEntity = FeatureEntity(
                id = candidateKey,
                hlc = initialHlc,
                isDeleted = false,
                textValue = "INITIAL_FILLED_TEXT",
                countValue = 10
            )
            integratedRepo.seed(initialEntity)
            integratedRepo.delete(candidateKey)

            val deletedEntity = integratedRepo.storedEntities[candidateKey]
            assertNotNull(deletedEntity, "Deleted entity must exist locally")
            assertTrue(deletedEntity.isDeleted, "Entity is locally deleted")

            // Device B: More recent remote upsert
            val remoteState = FeatureEntity(
                id = candidateKey,
                hlc = TestHlcFactory.createWithOffset(10.seconds),
                isDeleted = false,
                textValue = "",
                countValue = 10
            )
            val payload = integratedCodec.encode(remoteState, deletedEntity)
            val decodeContext = remoteState.deriveContext()
            assertNotNull(payload, "Payload before processRemoteIntent")

            // Device A: Ingest remote intent
            integratedRepo.processRemoteIntent(decodeContext, payload)
            val finalEntity = integratedRepo.storedEntities[candidateKey]
            assertNotNull(finalEntity, "Stored after processRemoteIntent")

            // Final State
            assertFalse(finalEntity.isDeleted, "Entity restored after remote upsert")
            assertEquals("", finalEntity.textValue, "Text value to be blank")
            assertEquals(initialEntity.countValue, finalEntity.countValue)
            assertNotEquals(deletedEntity.textValue, finalEntity.textValue)
            assertNotEquals(deletedEntity.fieldHlcs, finalEntity.fieldHlcs)

            // Side-effects
            assertEquals(1, intentStore.intents.size, "Only local deletion intent")
            assertEquals(1, workerHook.invalidationCount, "Only local deletion invalidation")
            assertEquals(1, hlcFactory.getNextHlcCallCount, "Only local delete hlc")
        }

    @Test
    fun processRemoteIntent_whenIncomingUpsertCaughtBetweenTwoLocalUpserts_mergesFieldsCorrectly() =
        runEnv {
            fakeClock.reverseTime(3.minutes)
            val integratedRepo = createCodecIntegratedRepo()
            val candidateKey = 109L
            val initialHlc = TestHlcFactory.createWithOffset((-3).minutes)
            setupValidContext(initialHlc)

            // Initial State at T0 (-3.minutes)
            val initialEntity = FeatureEntity(
                id = candidateKey,
                hlc = initialHlc,
                isDeleted = false,
                textValue = "INITIAL_TEXT",
                countValue = 10
            )
            integratedRepo.seed(initialEntity)

            // Device B: Encodes a single-field mutation (tag 4) at T1 (-2.minutes)
            val remoteState = initialEntity.copy(
                hlc = TestHlcFactory.createWithOffset((-2).minutes),
                textValue = "REMOTE_UPDATED_TEXT",
                countValue = 20
            )
            val payload = integratedCodec.encode(remoteState, initialEntity)
            val decodeContext = remoteState.deriveContext()
            assertNotNull(payload, "Payload before processRemoteIntent")

            // Device A: Performs local mutation to a separate field (tag 5) at T2 (> T1)
            fakeClock.advanceTime(3.minutes)
            val localResult = integratedRepo.upsert(candidateKey) { existing ->
                existing!!.copy(countValue = 99)
            }
            assertEquals(candidateKey, localResult)
            val localEntityBeforeRemote = integratedRepo.storedEntities[candidateKey]
            assertNotNull(localEntityBeforeRemote)
            assertEquals("INITIAL_TEXT", localEntityBeforeRemote.textValue)
            assertEquals(99, localEntityBeforeRemote.countValue)

            // Device A: Receives and ingests Device B's remote intent from T1
            integratedRepo.processRemoteIntent(decodeContext, payload)
            val finalEntity = integratedRepo.storedEntities[candidateKey]
            assertNotNull(finalEntity, "Stored after processRemoteIntent")

            // Final state assertions:
            // - Remote textValue (T1 > T0) is accepted and merged
            // - Local countValue (T2 > T0) is preserved
            assertEquals("REMOTE_UPDATED_TEXT", finalEntity.textValue)
            assertEquals(99, finalEntity.countValue, "Count value preserved")
            assertFalse(finalEntity.isDeleted)
            assertNotEquals(initialEntity.fieldHlcs, finalEntity.fieldHlcs)
            assertTrue(writer.logs.any { "Field Rejected [tag=5]" in it.message })

            // Side-effect Assertions: Inbound remote processing dispatches no extra intents or signals
            assertEquals(1, intentStore.intents.size, "Only local upsert intent recorded")
            assertEquals(1, workerHook.invalidationCount, "Only local upsert invalidation")
        }


}