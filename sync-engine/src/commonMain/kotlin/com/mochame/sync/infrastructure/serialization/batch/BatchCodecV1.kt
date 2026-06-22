package com.mochame.sync.infrastructure.serialization.batch

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.intent.IntentCodecV1
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.annotation.Single

@Serializable
private data class SyncBatchPayloadV1(
    val size: Int,
    val envelopes: List<ByteArray> // Raw byte streams produced by SyncIntentCodecV1
)


@ExperimentalSerializationApi
@Single
class BatchCodecV1(
    intentCodec: IntentCodecV1,
    logger: Logger
) : VersionedBatchCodec(
    version = 0x01,
    intentCodec = intentCodec,
    logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.SYNC,
        className = "BatchCodecV1"
    )
) {

    override fun encodePayload(intents: List<SyncIntent>): ByteArray {
        require(intents.isNotEmpty()) { "Cannot serialise an empty batch" }

        val serializedEnvelopes = intents.map { intent ->
            intentCodec.encode(intent)
        }

        val batchPayload = SyncBatchPayloadV1(
            size = serializedEnvelopes.size,
            envelopes = serializedEnvelopes
        )

        return ProtoBuf.encodeToByteArray(SyncBatchPayloadV1.serializer(), batchPayload)
    }

    override fun decodePayload(bytes: ByteArray): List<SyncIntent> {
        val batchPayload =
            ProtoBuf.decodeFromByteArray(SyncBatchPayloadV1.serializer(), bytes)

        return batchPayload.envelopes.map { envelopeBytes ->
            intentCodec.decode(envelopeBytes)
        }
    }

}