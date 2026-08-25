@file:OptIn(InternalTestApi::class)

package com.mochame.sync.orchestration

import com.mochame.annotations.AppBackgroundScope
import com.mochame.annotations.IoContext
import com.mochame.support.MochaPlatformTest
import com.mochame.support.awaitCondition
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
import com.mochame.utils.fixtures.TestNodeId
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import org.koin.core.qualifier.named
import org.koin.dsl.includes
import org.koin.dsl.module
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    // Inbound Processing
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

        assertEquals(2, transactor.executionCount, "Retry plus Success")
        assertTrue(scope.currentTime > initialTime, "Staggered Retry")
        assertEquals(1, stubA.invocationCount)
        assertEquals(listOf(hlc), nodeManager.updatedHlcFloors)
        assertEquals(1, codec.decodeCallCount)
    }

    @Test
    fun should_processConcurrentInboundCalls_and_advanceAllHlcFloorsWithoutLoss() =
        runEnv { scope ->
            bootManager.updateBootState(BootState.Ready)
            hlcFactory.hydrate(null, TestNodeId.A)

            val hlc1 = TestHlcFactory.createWithOffset(1.seconds)
            val hlc2 = TestHlcFactory.createWithOffset(2.seconds)

            // Given: simulation of suspending during intent1 processRemoteIntent
            val intent1 = createTestSyncIntent(
                hlc = hlc1,
                candidateKey = 10L,
                featureContext = FeatureContext.TEST_STUB_A
            )
            val intent2 = createTestSyncIntent(
                hlc = hlc2,
                candidateKey = 20L,
                featureContext = FeatureContext.TEST_STUB_A
            )
            codec.nextDecodeResult = listOf(intent1)

            val firstCallSuspended = CompletableDeferred<Unit>()
            val releaseFirstCall = CompletableDeferred<Unit>()

            stubA.onProcessHook = { _, _ ->
                firstCallSuspended.complete(Unit)
                releaseFirstCall.await()
            }

            scope.launch {
                coordinator.onInboundBytes(byteArrayOf(0))
            }
            firstCallSuspended.await()

            // Given: Execution context for intent2
            stubA.onProcessHook = null
            codec.nextDecodeResult = listOf(intent2)

            scope.launch {
                coordinator.onInboundBytes(byteArrayOf(0))
            }
            scope.runCurrent()

            // When: concurrent inbound calls are processed
            releaseFirstCall.complete(Unit)
            scope.advanceUntilIdle()

            // Then: both intents processed sequentially
            assertEquals(2, stubA.invocationCount)
            assertEquals(2, codec.decodeCallCount)
            assertEquals(hlc2, hlcFactory.getCurrentHlc())
            assertTrue(nodeManager.updatedHlcFloors.contains(hlc1))
            assertTrue(nodeManager.updatedHlcFloors.contains(hlc2))
        }

    // --- State Guards ---

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

    // --- HLC Floor Progression ---

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

    @Test
    fun should_isolateDownstreamException_and_preserveStreamLifecycleForSubsequentInvalidations() =
        runEnv { scope ->
            bootManager.updateBootState(BootState.Ready)

            val outboundJob = coordinator.startOutbound()
            scope.runCurrent()
            assertTrue(outboundJob.isActive, "Outbound pipeline collector must be active")

            // Given a failed intent
            val failedIntent = createTestSyncIntent(candidateKey = 1L)
            intentStore.seedIntents(failedIntent)
            intentStore.failWith = IllegalStateException("Disk I/O failure during claim")

            // When invalidation triggers
            workerHook.invalidate()
            scope.runCurrent()

            // Then a one time failure did not terminate the outbound pipeline
            assertTrue(outboundJob.isActive, "Collector job must remain active")
            assertEquals(1, intentStore.claimedBatchCallCount)
            assertEquals(0, codec.encodeCallCount)


            // Given a valid execution environment
            intentStore.failWith = null
            val recoveryIntent =
                createTestSyncIntent(candidateKey = 2L, hlc = TestHlcFactory.create(100L))
            intentStore.seedIntents(recoveryIntent)

            // When invalidation triggers
            workerHook.invalidate()
            scope.advanceUntilIdle()

            // Then pipeline recovered all intents
            assertTrue(outboundJob.isActive, "Collector job must still be running after recovery")
            assertEquals(3, intentStore.claimedBatchCallCount)
            assertEquals(1, codec.encodeCallCount)

            val encodedBatch = codec.encodedInvocations.first()
            assertEquals(2, encodedBatch.size)
            val claimedExample = encodedBatch.first()
            assertEquals(recoveryIntent.candidateKey, claimedExample.candidateKey)
            assertEquals(SyncStatus.SYNCING, claimedExample.syncStatus)
            assertNotNull(claimedExample.syncId)

            outboundJob.cancel()
        }

    @Test
    fun should_isolateCodecSerializationError_and_drainPendingQueueOnNextSignal() =
        runEnv { scope ->
            bootManager.updateBootState(BootState.Ready)

            val outboundJob = coordinator.startOutbound()
            scope.runCurrent()

            // Given incoming encoding exception
            val intent1 = createTestSyncIntent(candidateKey = 10L)
            intentStore.seedIntents(intent1)
            codec.encodeError = RuntimeException("Payload serialization buffer corrupted")

            // When worker triggered
            workerHook.invalidate()
            scope.runCurrent()

            // Then pipeline remains active and intent state is recoverable by Janitor
            assertTrue(outboundJob.isActive, "Stream must not crash on codec error")
            assertEquals(1, codec.encodeCallCount)
            val unprocessedIntent = codec.encodedInvocations[0].first()
            assertEquals(intent1.candidateKey, unprocessedIntent.candidateKey)
            assertEquals(SyncStatus.SYNCING, unprocessedIntent.syncStatus)
            val initialLeased = unprocessedIntent.leasedAt
            assertNotNull(unprocessedIntent.leasedAt)

            // Given consequential valid execution context
            codec.encodeError = null
            val intent2 =
                createTestSyncIntent(candidateKey = 20L, hlc = TestHlcFactory.create(ts = 100L))
            intentStore.seedIntents(intent2)

            // When worker triggered
            workerHook.invalidate()
            scope.advanceUntilIdle()

            // Then pipeline recovered second intent
            assertTrue(outboundJob.isActive)
            assertEquals(2, codec.encodeCallCount)
            val processedIntent = codec.encodedInvocations[1].first()
            assertEquals(intent2.candidateKey, processedIntent.candidateKey)
            assertEquals(SyncStatus.SYNCING, processedIntent.syncStatus)
            assertEquals(initialLeased, codec.encodedInvocations[1].first().leasedAt)

            outboundJob.cancel()
        }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Test
    fun should_abortInFlightBatchCleanly_when_jobCancelledDuringExecution() = runEnv { scope ->
        bootManager.updateBootState(BootState.Ready)

        // Given Coordinator is suspended on outbound pipeline
        val outboundJob = coordinator.startOutbound()
        scope.runCurrent()

        val intent = createTestSyncIntent(candidateKey = 20L)
        intentStore.seedIntents(intent)

        val batchClaimed = CompletableDeferred<Unit>()
        val releaseBatch = CompletableDeferred<Unit>()

        intentStore.onClaimHook = {
            batchClaimed.complete(Unit)
            releaseBatch.await()
        }

        workerHook.invalidate()
        scope.runCurrent()
        batchClaimed.await()

        // When Job is canceled
        outboundJob.cancel()
        scope.advanceUntilIdle()

        // Then outbound pipeline is canceled
        assertTrue(outboundJob.isCancelled)
        assertEquals(1, intentStore.claimedBatchCallCount)
        assertEquals(0, codec.encodeCallCount)
        releaseBatch.complete(Unit)
    }

    @Test
    fun should_stopCollectingSignals_immediatelyAfterJobCancellation() = runEnv { scope ->
        bootManager.updateBootState(BootState.Ready)

        // Given canceled job
        val outboundJob = coordinator.startOutbound()
        scope.runCurrent()

        outboundJob.cancel()
        scope.runCurrent()

        // When invalidation triggers
        repeat(3) {
            workerHook.invalidate()
        }
        scope.advanceUntilIdle()

        // Then
        assertEquals(0, intentStore.claimedBatchCallCount)
        assertEquals(0, codec.encodeCallCount)
        assertEquals(0, workerHook.totalCollects)
    }

    // -------------------------------------------------------------------------
    // Contention
    // -------------------------------------------------------------------------

    @Test
    fun concurrentInboundAndOutbound_withTransientDbBusy_recoversAndPersistsCorrectly() =
        runUnitEnvironment<SyncCoordinatorTestEnv>(
            bindTestScope = false,
            koinSetup = {
                includes(koinConfiguration<SyncCoordinatorTestApp>())

                modules(
                    module {
                        single<CoroutineDispatcher>(qualifier = named<IoContext>()) {
                            Dispatchers.Default
                        }

                        single<CoroutineScope>(qualifier = named<AppBackgroundScope>()) {
                            CoroutineScope(SupervisorJob() + Dispatchers.Default)
                        }
                    }
                )
            }
        ) { scope ->
            bootManager.updateBootState(BootState.Ready)
            hlcFactory.hydrate(null, TestNodeId.A)
            intentStore.failWith = MochaException.Transient.DatabaseBusy("Database busy")
            val outboundJob = coordinator.startOutbound()

            // Given: 3 inbound workers, 3 outbound workers - all staged
            val inboundWorkers = 3
            val outboundWorkers = 3
            val totalWorkers = inboundWorkers + outboundWorkers
            val operationsPerWorker = 3

            val readySignals = List(totalWorkers) { CompletableDeferred<Unit>() }
            val startGate = CompletableDeferred<Unit>()

            val inboundIntent = createTestSyncIntent(
                hlc = TestHlcFactory.createWithOffset(1.milliseconds),
                candidateKey = 999L,
                featureContext = FeatureContext.TEST_STUB_A
            )
            codec.nextDecodeResult = listOf(inboundIntent)

            val inboundJobs = List(inboundWorkers) { workerId ->
                scope.launch(Dispatchers.Default) {
                    readySignals[workerId].complete(Unit)
                    startGate.await()

                    repeat(operationsPerWorker) { opIndex ->
                        val payload = byteArrayOf(workerId.toByte(), opIndex.toByte())
                        coordinator.onInboundBytes(payload)
                    }
                }
            }

            val expectedOutboundKeys = (0 until outboundWorkers).flatMap { workerId ->
                (0 until operationsPerWorker).map { opIndex ->
                    (1000L * (workerId + 1)) + opIndex
                }
            }.toSet()

            val outboundJobs = List(outboundWorkers) { workerId ->
                scope.launch(Dispatchers.Default) {
                    readySignals[inboundWorkers + workerId].complete(Unit)
                    startGate.await()

                    repeat(operationsPerWorker) { opIndex ->
                        val uniqueOffset = ((workerId * operationsPerWorker) + opIndex + 1).seconds
                        val candidateKey = (1000L * (workerId + 1)) + opIndex
                        val hlc = TestHlcFactory.createWithOffset(uniqueOffset)

                        val intent = createTestSyncIntent(
                            candidateKey = candidateKey,
                            featureContext = FeatureContext.TEST_STUB_A,
                            hlc = hlc
                        )

                        hlcFactory.witness(hlc)
                        intentStore.seedIntents(intent)
                        workerHook.invalidate()
                    }
                }
            }

            // When: all workers execute in parallel
            readySignals.awaitAll()
            startGate.complete(Unit)
            (inboundJobs + outboundJobs).joinAll()

            awaitCondition(
                timeout = 8.seconds,
                message = "Outbound consumer did not finish encoding all 9 intents in time"
            ) {
                codec.encodedInvocations.flatten().size >= expectedOutboundKeys.size
            }

            // Then: Inbound
            val totalInboundOperations = inboundWorkers * operationsPerWorker
            assertEquals(totalInboundOperations, stubA.invocationCount, "All inbound transactions.")
            assertTrue(nodeManager.updatedHlcFloors.contains(inboundIntent.hlc), "HLC floor.")

            // Then: Outbound
            assertIntentsProperlyBatched(expectedKeys = expectedOutboundKeys)

            // Then: Side-Effects
            val expectedMaxHlc = TestHlcFactory.createWithOffset((outboundWorkers * operationsPerWorker).seconds)
            assertEquals(expectedMaxHlc, hlcFactory.getCurrentHlc())
            assertEquals(9, intentStore.intents.size)
            assertEquals(9, workerHook.invalidationCount)

            outboundJob.cancelAndJoin()
        }


}