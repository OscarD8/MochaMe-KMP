package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.di.CoordinatorMutex
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.node.IdGenerator
import com.mochame.contract.providers.TransactionProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.SyncReceiver
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.providers.tryWithLock
import com.mochame.sync.domain.serialization.BatchCodecRouter
import com.mochame.sync.domain.serialization.IntentCodecRouter
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds


@Single
internal class SyncCoordinator(
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    private val intentStore: SyncIntentMaintenanceStore,
    private val transactor: TransactionProvider,
//    private val moduleStateStore: SyncModuleStateStore,
    private val batchCodecRouter: BatchCodecRouter,
    private val intentCodecRouter: IntentCodecRouter,
    private val idGenerator: IdGenerator,
    @CoordinatorMutex private val coordinatorMutex: Mutex,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.SYNC,
        className = "Coordt"
    )

    private val receiverRoutingMap: Map<String, SyncReceiver> =
        receivers.associateBy { it.moduleContext.modelName }

    // awaiting implementation of the server
    // Called by the app's lifecycle owner on startup
    @OptIn(FlowPreview::class)
    suspend fun startOutboundPipeline() {
        intentStore.observePendingCount()
            .debounce(200.milliseconds) // due to potential overhead of Rooms invalidation trackers
            .collect { pendingCount ->
                if (pendingCount == 0) return@collect

                coordinatorMutex.tryWithLock {
                    val sessionId = idGenerator.nextId()

                    while (true) {
                        val batch = transactor.runImmediateTransaction {
                            val claimedRows = intentStore.claimBatch(sessionId)
                            if (claimedRows == 0) return@runImmediateTransaction emptyList() // necessary in case Janitor just performed manual sweep

                            intentStore.getClaimedBatch(sessionId)
                        }

                        if (batch.isEmpty()) break

                        try {
                            val payload = batchCodecRouter.versionEncode(
                                batch.map { it }
                            )
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
                            logger.w(e) { "Transmission failed for session: $sessionId. ${e.message}" }

                            break // Break loop; Janitor repairs stranded lease rows later.
                            // This is currently where all failed encoding/network attempts propagate, and then get silenced.
                        }
                    }
                }
            }
    }

//    private suspend fun packageAndShip(module: MochaModule) {
//        val pending = intentStore.getPendingByModule(module)
//        if (pending.isEmpty()) return
//
//        pending.filterNotNull().forEach { entity ->
//            val domain = entity.toDomain()
//            val bytes = syncCodec.versionedEncode(domain)
//            try {
//                networkApi.push(bytes)
//                intentStore.discardIntent(entity.hlc)
//                logger.i { "Shipped intent ${entity.candidateKey} for ${entity.model}" }
//            } catch (e: Exception) {
//                logger.w(e) { "Failed to ship ${entity.candidateKey}, will retry" }
//                // Leave as PENDING — Janitor or next flow emission handles retry
//            }
//        }
//    }

    //
    internal suspend fun onInboundBytes(raw: ByteArray) {
        val intents = try {
            batchCodecRouter.versionedDecode(raw)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected parsing failure during batch processing. ${e.message}" }
            return
        }

        intents.forEach { processIntent(it) }
    }

    private suspend fun processIntent(intent: SyncIntent) {
        val payload = intent.payload

        val receiver = receiverRoutingMap[intent.model] ?: run {
            logger.e { "Routing failure for model '${intent.model}'" }
            throw MochaException.Persistent.Internal(
                "No SyncReceiver for model string '${intent.model}'"
            )
        }

        if (payload == null) {
            if (intent.overflowBlobId != null) {
                intentStore.recordIntent(intent)
                logger.w { "Overflow intent staged: ${intent.candidateKey}" }
                return
            } else {
                logger.e { "Received null payload with no overflow reference for ${intent.candidateKey}" }
                throw MochaException.Persistent.CorruptionDetected("Null payload with no blobId for ${intent.candidateKey}")
            }
        }

        try {
            val intentContext = DecodeContext(
                id = intent.candidateKey,
                hlc = intent.hlc,
                lastModified = intent.createdAt,
                op = intent.operation
            )

            receiver.processRemoteIntent(intentContext, payload)
        } catch (e: Exception) {
            logger.w(e) { "Processing failed for ${intent.candidateKey}. Error : ${e.message}" }
        }
    }

}
