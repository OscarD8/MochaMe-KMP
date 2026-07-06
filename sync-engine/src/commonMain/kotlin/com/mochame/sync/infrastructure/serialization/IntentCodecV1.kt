package com.mochame.sync.infrastructure.serialization

import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.SyncStatus
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.serialization.IntentCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single
import kotlin.time.Clock

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class SyncIntentDeltaV1(
    @ProtoNumber(1) val featureSchemaVersion: Int,
    @ProtoNumber(2) val hlc: String,
    @ProtoNumber(3) val candidateKey: String,
    @ProtoNumber(4) val module: String,
    @ProtoNumber(5) val model: String,
    @ProtoNumber(6) val operation: String,
    @ProtoNumber(7) val payloadBlob: ByteArray? = null,
    @ProtoNumber(8) val overflowBlobId: String? = null,
    @ProtoNumber(9) val createdAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Single
internal class IntentCodecV1 : IntentCodec {

    override fun encode(intent: SyncIntent): ByteArray {
        val delta = SyncIntentDeltaV1(
            featureSchemaVersion = intent.featureSchemaVersion,
            hlc = intent.hlc.toString(),
            candidateKey = intent.candidateKey,
            module = intent.module,
            model = intent.model,
            operation = intent.operation.name,
            payloadBlob = intent.payload,
            overflowBlobId = intent.overflowBlobId,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        return ProtoBuf.encodeToByteArray(SyncIntentDeltaV1.serializer(), delta)
    }

    override fun decode(bytes: ByteArray): SyncIntent {
        val envelope = ProtoBuf.decodeFromByteArray(SyncIntentDeltaV1.serializer(), bytes)

        return SyncIntent(
            featureSchemaVersion = envelope.featureSchemaVersion,
            hlc = HLC.parse(envelope.hlc),
            candidateKey = envelope.candidateKey,
            module = envelope.module,
            model = envelope.model,
            operation = MutationOp.valueOf(envelope.operation),
            syncStatus = SyncStatus.RECEIVED,
            payload = envelope.payloadBlob,
            overflowBlobId = envelope.overflowBlobId,
            retryCount = 0,
            createdAt = envelope.createdAt
        )
    }
}