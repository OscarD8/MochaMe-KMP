package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.di.CoordinatorMutex
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.node.IdGenerator
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.platform.providers.TransactionProvider
import com.mochame.sync.data.toDomain
import com.mochame.sync.data.toEntity
import com.mochame.sync.domain.components.BatchCodecRegistry
import com.mochame.sync.domain.components.IntentCodecRegistry
import com.mochame.sync.domain.components.SyncReceiver
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.domain.providers.tryWithLock
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single


@Single
class SyncCoordinator(
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    private val intentStore: SyncIntentMaintenanceStore,
    private val transactor: TransactionProvider,
//    private val moduleStateStore: SyncModuleStateStore,
    private val batchCodecRegistry: BatchCodecRegistry,
    private val intentCodecRegistry: IntentCodecRegistry,
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
            .debounce(200) // due to potential overhead of Rooms invalidation trackers
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
                            val payload = batchCodecRegistry.encode(
                                batch.map { it.toDomain() }
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
                            logger.w(e) { "Transmission failed for session: $sessionId" }

                            break // Break loop; Janitor repairs stranded lease rows later
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
//            val bytes = syncCodec.encode(domain)
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
            batchCodecRegistry.decode(raw)
        } catch (e: MochaException.Persistent) {
            logger.e(e) { "Transport batch package completely corrupted or invalid" }
            return
        } catch (e: Exception) {
            logger.e(e) { "Unexpected parsing failure during batch processing" }
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

        // Claude: only necessary to persist in case of overflow.
        // If debugging is a nightmare change this?
        if (intent.payload == null && intent.overflowBlobId != null) {
            intentStore.recordIntent(intent.toEntity())
            logger.w { "Overflow intent staged: ${intent.candidateKey}" }
            return
        }

        try {
            val intentContext = DecodeContext(
                id = intent.candidateKey,
                hlc = intent.hlc,
                lastModified = intent.createdAt,
                op = intent.operation
            )

            receiver.processRemoteIntent(intentContext, intent.payload, intent.overflowBlobId)
        } catch (e: Exception) {
            logger.w(e) { "Processing failed for ${intent.candidateKey}. Error : ${e.message}" }
        }
    }

}
