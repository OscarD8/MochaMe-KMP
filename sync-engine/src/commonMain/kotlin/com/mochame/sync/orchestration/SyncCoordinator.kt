package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.contract.node.IdGenerator
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.data.toEntity
import com.mochame.sync.domain.components.SyncCodecRegistry
import com.mochame.sync.domain.components.SyncReceiver
import com.mochame.sync.domain.model.DecodeContext
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.domain.stores.SyncIntentMaintenanceStore
import com.mochame.sync.domain.stores.SyncModuleStateStore
import org.koin.core.annotation.Single


@Single
class SyncCoordinator(
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    private val intentStore: SyncIntentMaintenanceStore,
    private val moduleStateStore: SyncModuleStateStore,
    private val syncCodec: SyncCodecRegistry,
    private val idGenerator: IdGenerator,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.SYNC,
        className = "SyncCoord"
    )

    private val receiverRoutingMap: Map<String, SyncReceiver> =
        receivers.associateBy { it.module.modelName }

    // awaiting implementation of the server

    // Called by the app's lifecycle owner on startup
    suspend fun startOutboundPipeline() {
        intentStore.observePendingCount()
            .collect { pendingCount ->

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

    /**
     * This will blow up if any message other than a Mocha.Persistent comes through
     */
    internal suspend fun onInboundBytes(raw: ByteArray) {
        val intent = try {
            syncCodec.decode(raw)
        } catch (e: MochaException.Persistent) {
            logger.e(e) { "Sync envelope rejected: ${e.message}" }
            return  // unrecoverable — bad bytes, nothing to persist
        }

        // Only persist if we cannot process immediately - changing this to persist the intent always possibly
        // for error handling - janitor should be updated to ensure it knows how to handle received entities
//        if (intent.overflowBlobId != null && intent.payload == null) {
//            intentStore.recordIntent(intent.toEntity())
//            logger.w { "Inbound overflow intent persisted: ${intent.candidateKey}" }
//            return
//        }

        intentStore.recordIntent(intent.toEntity())

        try {
            processInbound(intent)
        } catch (e: MochaException.Persistent.Internal) {
            // Routing failure — no receiver registered
            // Intent is persisted, Janitor cannot fix this either
            // This is a deployment error, log loudly
            logger.e(e) { "Routing failure for model '${intent.model}'" }
        } catch (e: Exception) {
            // Processing failed but intent is persisted
            // Janitor will retry via processInbound
            // Build on overflow logic here
            logger.w(e) { "processInbound failed for ${intent.candidateKey}, Janitor will retry" }
        }
    }

    private suspend fun processInbound(intent: SyncIntent) {
        val receiver = receiverRoutingMap[intent.model]
            ?: throw MochaException.Persistent.Internal(
                "No SyncReceiver for model string '${intent.model}'"
            )

        val intentContext = DecodeContext(
            id = intent.candidateKey,
            hlc = intent.hlc,
            lastModified = intent.createdAt,
            op = intent.operation
        )

        receiver.processRemoteIntent(intentContext, intent.payload, intent.overflowBlobId)
    }

}
