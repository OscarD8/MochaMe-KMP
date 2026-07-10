package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.di.AppScope
import com.mochame.contract.di.CoordinatorMutex
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.node.IdGenerator
import com.mochame.contract.providers.TransactionProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.SyncInvalidationHook
import com.mochame.sync.contract.SyncReceiver
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.stores.SyncIntentStore
import com.mochame.sync.tryWithLock
import com.mochame.sync.domain.serialization.PayloadCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single


@Single
internal class SyncCoordinator(
    private val intentStore: SyncIntentStore,
    private val transactor: TransactionProvider,
//    private val featureStateStore: FeatureSyncStateStore,
    private val payloadCodec: PayloadCodec,
    private val idGenerator: IdGenerator,
    private val invalidationHook: SyncInvalidationHook,
    @CoordinatorMutex private val coordinatorMutex: Mutex,
    @AppScope private val appScope: CoroutineScope,
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.SYNC,
        className = "MsCord"
    )

    private val receiverRoutingMap: Map<String, SyncReceiver> =
        receivers.associateBy { it.featureContext.modelName }

    fun start() = appScope.launch {
        invalidationHook.signals.collect {
            try {
                processQueueUntilExhausted()
            } catch (e: Exception) {
                logger.e(e) {
                    "Failure inside outbound orchestration worker step. " +
                            "Isolating error to preserve background stream lifecycle."
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
                    if (claimedRows == 0) return@runImmediateTransaction emptyList() // necessary in case Janitor just performed manual sweep
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
                } catch (e: Exception) {
                    logger.w(e) { "Transmission failed for session: $batchId. ${e.message}" }

                    break // Break loop; Janitor repairs stranded lease rows later.
                    // This is currently where all failed encoding/network attempts propagate, and then get silenced.
                }
            }
        }

    }

    internal suspend fun onInboundBytes(inbound: ByteArray) {
        val intents = try {
            payloadCodec.decode(inbound)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected parsing failure during batch processing. ${e.message}" }
            return
        }

        intents.forEach { processIntent(it) }
    }

    private suspend fun processIntent(intent: SyncIntent) {

        val receiver = receiverRoutingMap[intent.model] ?: run {
            logger.e { "Routing failure for model '${intent.model}'" }
            throw MochaException.Persistent.Internal(
                "No SyncReceiver for model string '${intent.model}'"
            )
        }

        try {
            verifyIntentNullState(intent)

            val intentContext = extractContext(intent)

            receiver.processRemoteIntent(intentContext, intent.payload!!)

        } catch (e: MochaException) {
            logger.e { "Null field issue. Key: '${intent.candidateKey}. ${e.message}'" }
        } catch (e: Exception) {
            logger.w(e) { "Processing failed for ${intent.candidateKey}. Error : ${e.message}" }
        }
    }

    private suspend fun verifyIntentNullState(intent: SyncIntent) {
        check(intent.payload != null || intent.overflowBlobId != null) {
            throw MochaException.Persistent.CorruptionDetected(
                "Data integrity violation for ${intent.candidateKey}: both payload and blobId are null"
            )
        }
        check(!(intent.payload != null && intent.overflowBlobId != null)) {
            throw MochaException.Persistent.CorruptionDetected(
                "Data integrity violation for ${intent.candidateKey}: payload and blobId are mutually exclusive"
            )
        }

        if (intent.payload == null) {
            if (intent.overflowBlobId != null) {
                intentStore.recordIntent(intent)
                logger.w { "Overflow intent staged: ${intent.candidateKey}" }
                return
            } else {
                logger.e { "Received null payload with no overflow reference for ${intent.candidateKey}" }
                throw MochaException.Persistent.CorruptionDetected("Null payload with no blobId for ${intent.candidateKey}")
            }
        }
    }

    private fun extractContext(intent: SyncIntent) = DecodeContext(
        featureSchemaVersion = intent.featureSchemaVersion,
        id = intent.candidateKey,
        hlc = intent.hlc,
        lastModified = intent.createdAt,
        op = intent.operation
    )

}
