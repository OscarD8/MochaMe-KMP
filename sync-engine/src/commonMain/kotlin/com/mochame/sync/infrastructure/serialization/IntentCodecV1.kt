package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.domain.serialization.IntentCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class SyncIntentDeltaV1(
    @ProtoNumber(1) val featureSchemaVersion: Int,
    @ProtoNumber(2) val hlc: HLC,
    @ProtoNumber(3) val candidateKey: Long,
    @ProtoNumber(4) val module: String,
    @ProtoNumber(5) val model: String,
    @ProtoNumber(6) val operation: MutationOp = MutationOp.UNKNOWN,
    @ProtoNumber(7) val payloadBlob: ByteArray? = null,
    @ProtoNumber(8) val overflowBlobId: String? = null,
    @ProtoNumber(9) val createdAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Single
internal class IntentCodecV1(
    logger: Logger
) : IntentCodec {

    private val logger =
        logger.withTags(LogTags.Layer.SERI, LogTags.Domain.SYNC, "InCdc1")

    override fun encode(intent: SyncIntent): ByteArray {
        return try {
            val delta = SyncIntentDeltaV1(
                featureSchemaVersion = intent.featureSchemaVersion,
                hlc = intent.hlc,
                candidateKey = intent.candidateKey,
                module = intent.featureContext.featureName,
                model = intent.featureContext.modelName,
                operation = intent.operation,
                payloadBlob = intent.payload,
                overflowBlobId = intent.overflowBlobId,
                createdAt = intent.createdAt
            )

            val bytes = ProtoBuf.encodeToByteArray(SyncIntentDeltaV1.serializer(), delta)

            logger.v {
                "Encoded SyncIntent key=${intent.candidateKey} hlc=${intent.hlc} op=${intent.operation} " +
                        "v=${intent.featureSchemaVersion} size=${bytes.size}B" +
                        if (intent.overflowBlobId != null) " [Overflow: ${intent.overflowBlobId}]" else ""
            }

            bytes
        } catch (e: Exception) {
            logger.e(e) { "Failed to encode SyncIntent key=${intent.candidateKey} hlc=${intent.hlc}" }
            throw e
        }
    }

    override fun decode(bytes: ByteArray): SyncIntent {
        val envelope = try {
            ProtoBuf.decodeFromByteArray(SyncIntentDeltaV1.serializer(), bytes)
        } catch (e: Exception) {
            logger.e(e) { "Failed to deserialize SyncIntent delta (${bytes.size} bytes)" }
            throw e
        }

        return try {
            val intent = SyncIntent(
                featureSchemaVersion = envelope.featureSchemaVersion,
                hlc = envelope.hlc,
                candidateKey = envelope.candidateKey,
                featureContext = FeatureContext.fromModelString(envelope.model),
                operation = envelope.operation,
                syncStatus = SyncStatus.RECEIVED,
                payload = envelope.payloadBlob,
                overflowBlobId = envelope.overflowBlobId,
                retryCount = 0,
                createdAt = envelope.createdAt
            )

            logger.v {
                "Decoded SyncIntent key=${intent.candidateKey} hlc=${intent.hlc} op=${intent.operation} " +
                        "v=${intent.featureSchemaVersion} payload=${intent.payload?.size ?: 0}B"
            }

            intent
        } catch (e: Exception) {
            logger.e(e) {
                "Failed mapping delta key=${envelope.candidateKey} " +
                        "hlc=${envelope.hlc} op=${envelope.operation} model=${envelope.model}"
            }
            throw e
        }
    }
}