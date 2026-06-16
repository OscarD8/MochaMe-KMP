package com.mochame.sync.infrastructure.codec

import co.touchlab.kermit.Logger
import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.HLC
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.domain.state.SyncStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single
import kotlin.time.Clock


@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SyncIntentDeltaV1(
    @ProtoNumber(1) val hlc: String,
    @ProtoNumber(2) val candidateKey: String,
    @ProtoNumber(3) val module: String,
    @ProtoNumber(4) val model: String,
    @ProtoNumber(5) val operation: String,
    @ProtoNumber(6) val payloadBlob: ByteArray?,
    @ProtoNumber(7) val overflowBlobId: String? = null
)


@OptIn(ExperimentalSerializationApi::class)
@Single
class SyncCodecV1(logger: Logger) : BaseSyncCodec(
    version = 0x01,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "SyncCodecV1")
) {

    override fun encodePayload(intent: SyncIntent): ByteArray {
        val delta = SyncIntentDeltaV1(
            hlc = intent.hlc.toString(),
            candidateKey = intent.candidateKey,
            module = intent.module.moduleName,
            model = intent.model,
            operation = intent.operation.name,
            payloadBlob = intent.payload,
            overflowBlobId = intent.overflowBlobId
        )
        return ProtoBuf.encodeToByteArray(SyncIntentDeltaV1.serializer(), delta)
    }

    override fun decodePayload(bits: ByteArray): SyncIntent {
        val envelope =
            ProtoBuf.decodeFromByteArray(SyncIntentDeltaV1.serializer(), bits)

        return SyncIntent(
            hlc = HLC.parse(envelope.hlc),
            candidateKey = envelope.candidateKey,
            module = MochaModule.modelFromString(envelope.module),
            model = envelope.model,
            operation = MutationOp.valueOf(envelope.operation),
            syncStatus = SyncStatus.RECEIVED,
            syncId = null,
            payload = envelope.payloadBlob,
            diagnosticSummary = null,
            overflowBlobId = envelope.overflowBlobId,
            hasConflict = false,
            retryCount = 0,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    override fun validate(bytes: ByteArray): Boolean =
        bytes.size > 1 && bytes[0] == version

}