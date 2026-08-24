@file:OptIn(InternalTestApi::class)

package com.mochame.sync.orchestration

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.common.InternalTestApi
import com.mochame.sync.di.coordinator.SyncCoordinatorTestApp
import com.mochame.sync.di.coordinator.SyncCoordinatorTestEnv
import com.mochame.sync.domain.model.deriveContext
import com.mochame.sync.internal.fixtures.ReceivedIntent
import com.mochame.sync.internal.fixtures.createTestSyncIntent
import com.mochame.utils.fixtures.TestHlcFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private inline fun runEnv(crossinline block: suspend SyncCoordinatorTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment(
        koinSetup = { includes(koinConfiguration<SyncCoordinatorTestApp>()) },
        block = block
    )

@ExperimentalCoroutinesApi
class SyncCoordinatorTest : MochaPlatformTest() {

    // -------------------------------------------------------------------------
    // Boot Readiness
    // -------------------------------------------------------------------------

    @Test
    fun should_abortInboundProcessing_when_bootReadinessFails() = runEnv {
        bootManager.updateBootState(
            BootState.CriticalFailure(
                "Critical boot failure",
                IllegalStateException("Node boot corrupted")
            )
        )

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, codec.decodeCallCount)
        assertEquals(0, stubA.invocationCount)
        assertEquals(0, stubB.invocationCount)
        assertEquals(0, nodeManager.updatedHlcFloors.size)
    }

    @Test
    fun should_abortInboundProcessingAndKeepScopeAlive_when_payloadDecodingFails() = runEnv {
        bootManager.updateBootState(BootState.Ready)
        codec.decodeError = IllegalStateException("Malformed protobuf payload")

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, stubA.invocationCount)
        assertEquals(0, stubB.invocationCount)
        assertEquals(0, nodeManager.updatedHlcFloors.size)
    }

    @Test
    fun should_earlyExitCleanly_when_decodedBatchIsEmpty() = runEnv {
        bootManager.updateBootState(BootState.Ready)
        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(1, codec.decodeCallCount)
        assertEquals(0, stubA.invocationCount)
        assertEquals(0, stubB.invocationCount)
        assertEquals(0, nodeManager.updatedHlcFloors.size)
    }

    @Test
    fun should_abortOutboundPipeline_when_bootReadinessFails() = runEnv { scope ->
        bootManager.updateBootState(
            BootState.CriticalFailure(
                "Critical boot failure",
                IllegalStateException("Node boot corrupted")
            )
        )
        val job = coordinator.startOutbound()
        scope.runCurrent()

        assertFalse(job.isActive)
        workerHook.invalidate()
        scope.runCurrent()

        assertEquals(0, codec.encodeCallCount)
        assertEquals(0, intentStore.claimedBatchCallCount)
    }

    // -------------------------------------------------------------------------
    // Receiver Routing
    // -------------------------------------------------------------------------

    @Test
    fun should_routeIntentsToCorrectReceivers_and_deriveExactDecodeContext() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val hlcA = TestHlcFactory.create(ts = 1000L, count = 0)
        val hlcB = TestHlcFactory.create(ts = 1000L, count = 1)
        val payloadA = byteArrayOf(0x10, 0x20)
        val payloadB = byteArrayOf(0x30, 0x40)

        val intentA = createTestSyncIntent(
            hlc = hlcA,
            candidateKey = 101L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = payloadA,
            op = MutationOp.UPSERT,
            featureSchemaVersion = 2,
            changedMask = 0b101L
        )
        val intentB = createTestSyncIntent(
            hlc = hlcB,
            candidateKey = 202L,
            featureContext = FeatureContext.TEST_STUB_B,
            payload = payloadB,
            op = MutationOp.DELETE,
            featureSchemaVersion = 1,
            changedMask = 0b010L
        )
        val contextA = intentA.deriveContext()
        val contextB = intentB.deriveContext()

        codec.nextDecodeResult = listOf(intentA, intentB)
        coordinator.onInboundBytes(ByteArray(0))

        // Verify Stub A Invocations
        assertEquals(ReceivedIntent(contextA, payloadA), stubA.lastInvocation)
        assertEquals(ReceivedIntent(contextB, payloadB), stubB.lastInvocation)
        assertEquals(1, stubA.invocationCount)
        assertEquals(1, stubB.invocationCount)
    }

    // -------------------------------------------------------------------------
    // State Guards - Overflow
    // -------------------------------------------------------------------------

    @Test
    fun should_rejectIntent_when_bothPayloadAndOverflowBlobIdAreNull() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val invalidIntent = createTestSyncIntent(
            candidateKey = 301L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = null,
            overflowBlobId = null
        )

        codec.nextDecodeResult = listOf(invalidIntent)
        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, stubA.invocationCount)
        assertEquals(0, intentStore.intents.size)
        assertEquals(0, nodeManager.updatedHlcFloors.size)
    }

    @Test
    fun should_rejectIntent_when_bothPayloadAndOverflowBlobIdArePresent() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val invalidIntent = createTestSyncIntent(
            candidateKey = 302L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = byteArrayOf(0x05),
            overflowBlobId = "blob_overflow_123"
        )

        codec.nextDecodeResult = listOf(invalidIntent)
        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, stubA.invocationCount)
        assertEquals(0, intentStore.intents.size)
        assertEquals(0, nodeManager.updatedHlcFloors.size)
    }

    @Test
    fun should_stageInStore_and_dispatchToReceiver_when_intentIsLegitimateOverflow() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val overflowIntent = createTestSyncIntent(
            candidateKey = 303L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = null,
            overflowBlobId = "blob_valid_999"
        )

        codec.nextDecodeResult = listOf(overflowIntent)
        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(1, intentStore.intents.size)
        assertEquals(303L, intentStore.intents.first().candidateKey)

        assertEquals(1, stubA.invocationCount)
        val received = stubA.lastInvocation!!
        assertNull(received.payload)
        assertEquals("blob_valid_999", received.context.overflowBlobId)
    }

    // -------------------------------------------------------------------------
    // Fault Isolation / Recovery
    // -------------------------------------------------------------------------

    @Test
    fun should_isolateFailingReceiver_and_allowSubsequentIntentsToSucceed() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val hlcA = TestHlcFactory.create(ts = 1000L, count = 0)
        val hlcB = TestHlcFactory.create(ts = 2000L, count = 0)

        val failingIntent = createTestSyncIntent(
            hlc = hlcA,
            candidateKey = 401L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = byteArrayOf(0x01)
        )
        val successfulIntent = createTestSyncIntent(
            hlc = hlcB,
            candidateKey = 402L,
            featureContext = FeatureContext.TEST_STUB_B,
            payload = byteArrayOf(0x02)
        )
        codec.nextDecodeResult = listOf(failingIntent, successfulIntent)
        stubA.shouldFail = RuntimeException("Database constraint violation in Receiver A")

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(1, stubA.invocationCount)
        assertEquals(1, stubB.invocationCount)
        assertEquals(listOf(hlcB), nodeManager.updatedHlcFloors)
        assertTrue(hlcFactory.witnessedHlcs.contains(hlcB))
        assertFalse(hlcFactory.witnessedHlcs.contains(hlcA))
    }

    @Test
    fun should_isolateUnregisteredFeatureContext_withoutPoisoningBatch() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val hlcB = TestHlcFactory.create(ts = 5000L, count = 0)

        val unroutableIntent = createTestSyncIntent(
            candidateKey = 501L,
            featureContext = FeatureContext.UNRECOGNIZED_MODEL,
            payload = byteArrayOf(0x01)
        )
        val validIntent = createTestSyncIntent(
            hlc = hlcB,
            candidateKey = 502L,
            featureContext = FeatureContext.TEST_STUB_B,
            payload = byteArrayOf(0x02)
        )
        codec.nextDecodeResult = listOf(unroutableIntent, validIntent)

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, stubA.invocationCount)
        assertEquals(1, stubB.invocationCount)
        assertEquals(listOf(hlcB), nodeManager.updatedHlcFloors)
    }

    @Test
    fun should_retryAndRecover_when_transactorThrowsDatabaseBusy() = runEnv { scope ->
        bootManager.updateBootState(BootState.Ready)

        val hlc = TestHlcFactory.create(ts = 1000L)
        val intent = createTestSyncIntent(
            hlc = hlc,
            candidateKey = 101L,
            featureContext = FeatureContext.TEST_STUB_A
        )
        codec.nextDecodeResult = listOf(intent)
        transactor.shouldThrow = MochaException.Transient.DatabaseBusy("SQLite database locked")
        val initialTime = scope.currentTime

        coordinator.onInboundBytes(byteArrayOf(0x01))

        // Assert transactor ran twice (1 failed attempt + 1 successful recovery)
        assertEquals(2, transactor.executionCount)
        // Assert delay staggered the retry (initial delay is 10ms with jitter)
        assertTrue(scope.currentTime > initialTime)
        // Assert receiver executed and state updated upon recovery
        assertEquals(1, stubA.invocationCount)
        assertEquals(listOf(hlc), nodeManager.updatedHlcFloors)
    }

    // -------------------------------------------------------------------------
    // HLC Floor Progression
    // -------------------------------------------------------------------------

    @Test
    fun should_advanceHlcFloorToMaxSuccessfulTimestamp_when_orderIsInterleaved() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val hlc1 = TestHlcFactory.create(ts = 1000L, count = 0)
        val hlc2Failing = TestHlcFactory.create(ts = 5000L, count = 0)
        val hlc3 = TestHlcFactory.create(ts = 3000L, count = 0)

        val intent1 = createTestSyncIntent(
            hlc = hlc1,
            candidateKey = 601L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = byteArrayOf(0x01)
        )
        val intent2 = createTestSyncIntent(
            hlc = hlc2Failing,
            candidateKey = 602L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = null,
            overflowBlobId = null
        )
        val intent3 = createTestSyncIntent(
            hlc = hlc3,
            candidateKey = 603L,
            featureContext = FeatureContext.TEST_STUB_B,
            payload = byteArrayOf(0x03)
        )
        codec.nextDecodeResult = listOf(intent1, intent2, intent3)

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(listOf(hlc3), nodeManager.updatedHlcFloors)
        assertEquals(listOf(hlc3), hlcFactory.witnessedHlcs)
    }

    @Test
    fun should_notAdvanceHlcFloor_when_allIntentsInBatchFail() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val hlcA = TestHlcFactory.create(ts = 1000L, count = 0)
        val invalidIntent = createTestSyncIntent(
            hlc = hlcA,
            candidateKey = 701L,
            featureContext = FeatureContext.TEST_STUB_A,
            payload = null,
            overflowBlobId = null
        )
        codec.nextDecodeResult = listOf(invalidIntent)

        coordinator.onInboundBytes(ByteArray(0))

        assertEquals(0, nodeManager.updatedHlcFloors.size)
        assertEquals(0, hlcFactory.witnessedHlcs.size)
    }

    // -------------------------------------------------------------------------
    // Outbound Queue
    // -------------------------------------------------------------------------

    @Test
    fun should_exitCleanly_withoutEncoding_when_claimBatchReturnsZeroRows() = runEnv { scope ->
        bootManager.updateBootState(BootState.Ready)

        val outboundJob = coordinator.startOutbound()
        scope.runCurrent()

        workerHook.invalidate()
        scope.runCurrent()

        assertEquals(1, intentStore.claimedBatchCallCount)
        assertEquals(0, codec.encodeCallCount)

        outboundJob.cancel()
    }

    @Test
    fun should_processAllAvailableBatches_until_queueIsEmpty() = runEnv {
        bootManager.updateBootState(BootState.Ready)
        val hlc1 = TestHlcFactory.create(ts = 100)
        val hlc2 = TestHlcFactory.create(ts = 200)

        val intent1 = createTestSyncIntent(candidateKey = 1L, hlc = hlc1)
        val intent2 = createTestSyncIntent(candidateKey = 2L, hlc = hlc2)
        intentStore.seedIntents(intent1, intent2)

        coordinator.processQueueUntilExhausted()

        // Iteration 1: Claims all pending rows (2), encodes batch
        // Iteration 2: Queue empty, claims 0 rows, breaks while-loop
        val encodedBatch = codec.encodedInvocations.first()
        assertEquals(1, codec.encodeCallCount)
        assertEquals(2, encodedBatch.size)
        assertEquals(SyncStatus.SYNCING, encodedBatch.first().syncStatus)
        assertEquals(SyncStatus.SYNCING, encodedBatch.last().syncStatus)
        assertEquals(2, intentStore.claimedBatchCallCount)
    }

    @Test
    fun should_breakProcessLoop_when_payloadEncodingFails() = runEnv {
        bootManager.updateBootState(BootState.Ready)

        val intent1 = createTestSyncIntent(candidateKey = 10L)
        val intent2 = createTestSyncIntent(candidateKey = 20L)
        intentStore.seedIntents(intent1, intent2)
        codec.encodeError = IllegalArgumentException("Serialization buffer overflow")

        coordinator.processQueueUntilExhausted()

        assertEquals(1, intentStore.claimedBatchCallCount)
        assertEquals(1, codec.encodeCallCount)
    }

    @Test
    fun should_coalesceInvalidationBurst_and_processNewlyAddedIntentsInSameSweep() =
        runEnv { scope ->
            bootManager.updateBootState(BootState.Ready)

            val outboundJob = coordinator.startOutbound()
            scope.runCurrent()

            // Arrange Batch 1
            val batch1Intent = createTestSyncIntent(candidateKey = 1L)
            intentStore.seedIntents(batch1Intent)

            val batch1Claimed = CompletableDeferred<Unit>()
            val releaseBatch1 = CompletableDeferred<Unit>()

            intentStore.onClaimHook = {
                batch1Claimed.complete(Unit)
                releaseBatch1.await()
            }

            // Act Batch 1
            workerHook.invalidate()
            scope.runCurrent()
            batch1Claimed.await() // ensure Coordinator is suspended processing claim

            // Arrange Batch 2 and invalidation
            val batch2Intent =
                createTestSyncIntent(candidateKey = 2L, hlc = TestHlcFactory.create(ts = 100))
            intentStore.seedIntents(batch2Intent)
            intentStore.onClaimHook = null

            repeat(5) {
                workerHook.invalidate()
            }
            scope.runCurrent()

            // Unblock the initial sweep
            releaseBatch1.complete(Unit)
            scope.advanceUntilIdle()

            // Assert Batch 1 transition: state mutated to SYNCING with a generated batch ID
            val encodedBatch1 = codec.encodedInvocations[0]
            assertEquals(1, encodedBatch1.size, "Batch 1 size")
            val claimed1 = encodedBatch1.first()
            assertEquals(batch1Intent.candidateKey, claimed1.candidateKey)
            assertEquals(batch1Intent.hlc, claimed1.hlc)
            assertEquals(SyncStatus.SYNCING, claimed1.syncStatus)
            assertNotNull(claimed1.syncId)

            // Assert Batch 2 transition: state mutated to SYNCING with a distinct batch ID
            val encodedBatch2 = codec.encodedInvocations[1]
            assertEquals(1, encodedBatch2.size, "Batch 2 size")
            val claimed2 = encodedBatch2.first()
            assertEquals(batch2Intent.candidateKey, claimed2.candidateKey)
            assertEquals(batch2Intent.hlc, claimed2.hlc)
            assertEquals(SyncStatus.SYNCING, claimed2.syncStatus)
            assertNotNull(claimed2.syncId)

            assertTrue(claimed1.syncId != claimed2.syncId, "Claimed batch must be distinct")

            // Assert Side-Effects
            assertEquals(2, codec.encodeCallCount)
            assertEquals(6, workerHook.invalidationCount)

            outboundJob.cancel()
        }

    // -------------------------------------------------------------------------
    // Outbound Queue
    // -------------------------------------------------------------------------

}