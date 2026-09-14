package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.annotations.CoordinatorMutex
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.domain.model.deriveContext
import com.mochame.sync.spi.domain.QuarantinedPayloadStore
import com.mochame.sync.spi.domain.SyncIntentMaintenanceStore
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.spi.infrastructure.serialization.IntentCodec
import com.mochame.sync.spi.infrastructure.serialization.PayloadCodec
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.spi.network.SendResult
import com.mochame.sync.spi.network.SyncTransport
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.orchestration.SyncCoordinator
import com.mochame.sync.spi.policy.ExecutionPolicy
import com.mochame.sync.tryWithLock
import com.mochame.utils.interfaces.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single
import kotlin.time.TimeSource


@Single(binds = [SyncCoordinator::class])
internal class DefaultSyncCoordinator(
    private val transactor: TransactionProvider,
    private val syncTransport: SyncTransport,
    private val payloadCodec: PayloadCodec,
    private val intentCodec: IntentCodec,
    private val idGenerator: IdGenerator,
    private val executor: ExecutionPolicy,
    private val hlcFactory: HlcFactory,
    private val workerHook: SyncWorkerHook,
    private val bootManager: BootStatusProvider,
    private val nodeManager: NodeContextManager,
    private val timeUtils: TimeUtils,
    private val intentStore: SyncIntentMaintenanceStore,
    private val quarantinedPayloadStore: QuarantinedPayloadStore,
    @CoordinatorMutex private val coordinatorMutex: Mutex,
    @AppBackgroundScope private val appBackgroundScope: CoroutineScope,
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    logger: Logger
) : SyncCoordinator {
    private val logger =
        logger.withTags(LogTags.Layer.ORCH, LogTags.Domain.SYNC, "MsCord")

    private val receiverRoutingMap: Map<FeatureContext, SyncReceiver> =
        receivers.associateBy { it.featureContext }

    private val SyncIntent.receiver: SyncReceiver
        get() = receiverRoutingMap[featureContext] ?: run {
            logger.e { "Routing failure for feature context '$featureContext'" }
            throw MochaException.Persistent.StateIssue(
                "No SyncReceiver for feature context '$featureContext'"
            )
        }

    /**
     * No Mutex here.
     */
    override fun startOutboundListener(): Job = appBackgroundScope.launch {
        try {
            bootManager.awaitReady()
        } catch (e: Exception) {
            logger.e(e) { "Outbound sync pipeline disabled: boot readiness check failed." }
            return@launch
        }

        workerHook.signals.collect {
            try {
                logger.v { "Processing outbound batch..." }
                processQueueUntilExhausted()
            } catch (e: Exception) {
                logger.e(e) {
                    "Failure during outbound batch processing: ${e.message}. " +
                            "Preserving outbound pipeline state."
                }
            }
        }
    }

    /**
     * Intended behavior should ensure regular batches are made when feature repositories
     * perform local changes, these batches being small. The UI design must be considered
     * in relation to this behavior, as it will directly relate to how repositories trigger
     * invalidation and the batch process.
     */
    @OptIn(FlowPreview::class)
    override suspend fun processQueueUntilExhausted() {
        coordinatorMutex.tryWithLock {

            if (!syncTransport.isConnected) {
                logger.v { "Outbound: Transport disconnected. Skipping queue processing." }
                return
            }

            while (true) {
                val batchId = idGenerator.nextId()
                val batch = intentStore.claimAndGetBatch(batchId)
                if (batch.isEmpty()) break

                val payload = try {
                    payloadCodec.encode(batch)
                } catch (e: Exception) {
                    logger.e(e) { "Outbound: Batch $batchId encoding failed. Isolating corrupt intents." }

                    transactor.runImmediateTransaction {
                        val validIntents = mutableListOf<SyncIntent>()

                        for (intent in batch) {
                            try {
                                intentCodec.encode(intent)
                                validIntents.add(intent)
                            } catch (e: Exception) {
                                intentStore.quarantineIntent(
                                    hlc = intent.hlc,
                                    candidateKey = intent.candidateKey,
                                    errorMessage = e.message ?: "Codec serialization failure"
                                )
                                logger.e(e) { "Outbound: Quarantined corrupt intent [key=${intent.candidateKey}]" }
                            }
                        }

                        if (validIntents.isNotEmpty()) {
                            intentStore.releaseIntents(validIntents.map { it.hlc })
                        }
                    }

                    continue
                }

                when (val result = syncTransport.send(payload)) {
                    is SendResult.Success -> {
                        intentStore.acknowledgeSuccess(batchId)
                    }

                    is SendResult.NoConnection -> {
                        logger.w { "Outbound: No connection for batch $batchId. Awaiting reconnection." }
                        break
                    }

                    is SendResult.Failure -> {
                        intentStore.stampLastError(
                            batchId,
                            result.cause.message ?: "Transmission failure"
                        )
                        logger.w(result.cause) { "Outbound: Failed to transmit batch $batchId. Janitor to reconcile." }
                    }
                }
            }
        }
    }

    override suspend fun onInboundBytes(watermark: Long, inbound: ByteArray) {
        logger.v { "Inbound: Received batch with ${inbound.size}B..." }

        try {
            bootManager.awaitReady()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Inbound [watermark-$watermark]: Node is not ready or failed boot." }
            return
        }

        val intents = try {
            payloadCodec.decode(inbound)
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            logger.e(e) { "Inbound [watermark-$watermark]: Parsing failure during batch processing (${inbound.size}B). ${e.message}" }

            transactor.runImmediateTransaction {
                quarantinedPayloadStore.record(
                    watermark = watermark,
                    rawPayload = inbound,
                    failureReason = e.message ?: "Codec decode failure"
                )
                nodeManager.recogniseServerResponse(watermark, timeUtils.now())
            }
            return
        }

        if (intents.isEmpty()) {
            logger.e { "Empty List returned (from: ${inbound.size}B)." }
            transactor.runImmediateTransaction {
                nodeManager.recogniseServerResponse(watermark, timeUtils.now())
            }
            return
        }

        var maxValidHlc: HLC? = null
        val mark = TimeSource.Monotonic.markNow()
        var acceptedCount = 0

        executor.execute("Inbound-Watermark[$watermark]") {
            transactor.runImmediateTransaction {
                intents.forEach { intent ->
                    try {
                        orchestrateIntent(intent)
                        maxValidHlc = maxValidHlc?.let { maxOf(it, intent.hlc) } ?: intent.hlc
                        acceptedCount++
                    } catch (e: Exception) {
                        when (e) {
                            is CancellationException -> throw e
                            is MochaException.Persistent.StateIssue, is MochaException.Persistent.UnknownProtocolVersion -> {
                                intentStore.recordIntent(
                                    intent.copy(
                                        syncStatus = SyncStatus.QUARANTINED,
                                        lastErrorMessage = e.message,
                                        leasedAt = null,
                                        batchId = null
                                    )
                                )
                                logger.w { "Inbound [watermark-$watermark]: Quarantined decoded intent [hlc=${intent.hlc}] [key=${intent.candidateKey}]" }
                            }

                            else -> {
                                logger.e(e) { "Inbound [watermark-$watermark]: Unexpected Error. Aborting due to local environmental failure." }
                                throw e
                            }
                        }
                    }
                }

                maxValidHlc?.let {
                    hlcFactory.witness(it)
                    nodeManager.updateHlcFloor(it)
                }
                nodeManager.recogniseServerResponse(watermark, timeUtils.now())
            }
        }

        logger.i { "Inbound [watermark-$watermark]: Accepted $acceptedCount intent(s)".withTimer(mark) }
    }

    /**
     * All individual Intent processing from inbound ingestion will have errors propagate
     * to this boundary.
     *
     * There may be a need to update the intent status here to specifically mark it as a
     * received intent?
     */
    private suspend fun orchestrateIntent(intent: SyncIntent) {
        val intentContext = intent.checkOverflowState().deriveContext()
        intent.receiver.processRemoteIntent(intentContext, intent.payload)
    }

    private suspend fun SyncIntent.checkOverflowState(): SyncIntent {
        val hasPayload = payload != null
        val hasBlobId = overflowBlobId != null

        if (hasPayload == hasBlobId) {
            val message =
                if (!hasPayload) "both payload and blobId are null" else "payload and blobId are mutually exclusive"
            throw MochaException.Persistent.StateIssue("Data integrity violation: $message")
        }

        if (!hasPayload) {
            intentStore.recordIntent(this)
            logger.w { "Overflow intent staged: $candidateKey" }
        }

        return this
    }

}
