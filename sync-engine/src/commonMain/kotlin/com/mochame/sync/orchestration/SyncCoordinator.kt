package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.annotations.AppBackgroundScope
import com.mochame.annotations.CoordinatorMutex
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.logger.withTimer
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.domain.model.deriveContext
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.tryWithLock
import com.mochame.sync.spi.infrastructure.serialization.PayloadCodec
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single
import kotlin.time.TimeSource


@Single
internal class SyncCoordinator(
    private val intentStore: SyncIntentStore,
    private val transactor: TransactionProvider,
    private val payloadCodec: PayloadCodec,
    private val idGenerator: IdGenerator,
    private val executor: ExecutionPolicy,
    private val hlcFactory: HlcFactory,
    private val workerHook: SyncWorkerHook,
    private val bootManager: BootStatusProvider,
    private val nodeManager: NodeContextManager,
    @CoordinatorMutex private val coordinatorMutex: Mutex,
    @AppBackgroundScope private val appBackgroundScope: CoroutineScope,
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    logger: Logger
) {
    private val logger =
        logger.withTags(LogTags.Layer.ORCH, LogTags.Domain.SYNC, "MsCord")

    private val receiverRoutingMap: Map<FeatureContext, SyncReceiver> =
        receivers.associateBy { it.featureContext }

    fun startOutbound(): Job = appBackgroundScope.launch {
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


    // awaiting implementation of the server
    // Called by the app's lifecycle owner on startup
    /**
     * Intended behavior should ensure regular batches are made when feature repositories
     * perform local changes, these batches being small. The UI design must be considered
     * in relation to this behavior, as it will directly relate to how repositories trigger
     * invalidation and the batch process.
     */
    @OptIn(FlowPreview::class)
    suspend fun processQueueUntilExhausted() {
        coordinatorMutex.tryWithLock {
            while (true) {
                val batchId = idGenerator.nextId()

                val batch = transactor.runImmediateTransaction {
                    val claimedRows = intentStore.claimBatch(batchId)
                    if (claimedRows == 0) return@runImmediateTransaction emptyList()
                    intentStore.getClaimedBatch(batchId)
                }

                if (batch.isEmpty()) break

                try {
                    val payload = payloadCodec.encode(batch)

//                            val response = networkApi.push(payload)
//                            val accepted = response.results.filter { it.accepted }.map { it.hlc }
//                            val rejected = response.results.filter { !it.accepted }

//                            intentStore.acknowledgeSuccess(accepted.map { it.hlc })
//
//                            rejected.forEach { result ->
//                                intentStore.stampLastError(
//                                    hlcs = listOf(result.hlc),
//                                    message = result.errorMessage ?: "Server rejected intent"
//                                )
//                            }
//                              is this where the outbound flow suspends and waits to get some kind of server
//                              ack, and then calls node manager to recognizeResponse? Or separate method the server pings?

                } catch (e: Exception) {
                    logger.w(e) { "Transmission failed for session: $batchId. ${e.message}" }

                    break // Break loop; Janitor repairs stranded lease rows later.
                    // This is currently where all failed encoding/network attempts propagate, and then get silenced.
                }
            }
        }
    }

    internal suspend fun onInboundBytes(inbound: ByteArray) {
        try {
            bootManager.awaitReady()
        } catch (e: Exception) {
            logger.e(e) { "Inbound batch rejected: node is not ready or failed boot." }
            return
        }

        val intents = try {
            payloadCodec.decode(inbound)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected parsing failure during batch processing (${inbound.size}B). ${e.message}" }
            return
        }

        if (intents.isEmpty()) {
            logger.e { "Empty List returned (from: ${inbound.size}B)." }
            return
        }

        var maxValidHlc: HLC? = null
        val mark = TimeSource.Monotonic.markNow()

        try {
            executor.execute("InboundBatch_${intents.size}") {
                transactor.runImmediateTransaction {
                    intents.forEach { intent ->
                        val succeeded = orchestrateIntent(intent)
                        if (succeeded) {
                            maxValidHlc = maxValidHlc?.let { maxOf(it, intent.hlc) } ?: intent.hlc
                        }
                    }
                    maxValidHlc?.let {
                        hlcFactory.witness(it)
                        nodeManager.updateHlcFloor(it)
                    }
                }
            }
        } catch (e: Exception) {
            logger.e { "Caught Exception processing intents: ${e.message}. Inbound (${inbound.size}B)." }
            // Handle server logic here.
            return
        }


        logger.i { "Batch processing finalized".withTimer(mark) }

        // is this where the server logic should ultimately lead to a call to nodeContextManager
        // to call its recognizeServerResponse method? When doing this consider if this is the
        // only place that makes the call, is it possible to provide data that has a lower
        // timestamp than the database holds at any given moment? I am not implementing any checks
        // there right now.
    }

    /**
     * All individual Intent processing from inbound ingestion will have errors propagate
     * to this boundary.
     *
     * There may be a need to update the intent status here to specifically mark it as a
     * received intent?
     */
    private suspend fun orchestrateIntent(intent: SyncIntent): Boolean {
        return try {
            val intentContext = intent.checkOverflowState().deriveContext()
            intent.receiver.processRemoteIntent(intentContext, intent.payload)
            true
        } catch (e: Exception) {
            logger.w(e) { "[Key: ${intent.candidateKey}] ${e.message}" }
            false
        }
    }

    private suspend fun SyncIntent.checkOverflowState(): SyncIntent {
        val hasPayload = payload != null
        val hasBlobId = overflowBlobId != null

        if (hasPayload == hasBlobId) {
            val message =
                if (!hasPayload) "both payload and blobId are null" else "payload and blobId are mutually exclusive"
            throw MochaException.Persistent.StateIssue("Data integrity violation for $candidateKey: $message")
        }

        if (!hasPayload) {
            intentStore.recordIntent(this)
            logger.w { "Overflow intent staged: $candidateKey" }
        }

        return this
    }

    private val SyncIntent.receiver: SyncReceiver
        get() = receiverRoutingMap[featureContext] ?: run {
            logger.e { "Routing failure for feature context '$featureContext'" }
            throw MochaException.Persistent.Internal(
                "No SyncReceiver for feature context '$featureContext'"
            )
        }

}
