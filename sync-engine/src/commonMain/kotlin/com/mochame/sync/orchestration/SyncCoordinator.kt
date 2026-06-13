package com.mochame.sync.orchestration

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.toEntity
import com.mochame.sync.domain.components.SyncCodecRegistry
import com.mochame.sync.domain.components.SyncReceiver
import com.mochame.sync.domain.model.EntityMetadata
import com.mochame.sync.domain.stores.SyncIntentStore
import com.mochame.sync.infrastructure.codec.SyncCodec
import org.koin.core.annotation.Single


@Single
class SyncCoordinator(
    receivers: List<SyncReceiver>, // koin handles as long as classes are bound
    private val intentStore: SyncIntentStore,
    private val syncCodec: SyncCodecRegistry,
    logger: Logger
) {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.SYNC,
        className = "SyncCoord"
    )

    // Built once at boot from all registered SyncReceivers
    private val receiverRoutingTable: Map<String, SyncReceiver> =
        receivers.associateBy { it.module.modelName }

    internal suspend fun onInboundBytes(raw: ByteArray) {
        val intent = try {
            syncCodec.decode(raw)
        } catch (e: MochaException.Persistent) {
            logger.e(e) { "Sync envelope rejected: ${e.message}" }
            return  // unrecoverable — bad bytes, nothing to persist
        }

        val entity = intent.toEntity()
        intentStore.recordIntent(entity)

        try {
            processInbound(entity)
        } catch (e: MochaException.Persistent.Internal) {
            // Routing failure — no receiver registered
            // Intent is persisted, Janitor cannot fix this either
            // This is a deployment error, log loudly
            logger.e(e) { "Routing failure for model '${entity.model}'" }
        } catch (e: Exception) {
            // Processing failed but intent is persisted
            // Janitor will retry via processInbound
            logger.w(e) { "processInbound failed for ${entity.candidateKey}, Janitor will retry" }
        }
    }

    private suspend fun processInbound(intent: SyncIntentEntity) {
        val receiver = receiverRoutingTable[intent.model]
            ?: throw MochaException.Persistent.Internal(
                "No SyncReceiver for model string '${intent.model}'"
            )

        val metadata = EntityMetadata(
            id = intent.candidateKey,
            hlc = HLC.parse(intent.hlc),
            lastModified = intent.createdAt,
            op = intent.operation
        )

        receiver.processRemoteIntent(metadata, intent.payload ?: byteArrayOf())
    }
}
