package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.domain.serialization.IntentCodecRouter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.annotation.Single

@Serializable
private data class SyncBatchPayloadV1(
    val size: Int,
    val envelopes: List<ByteArray>
)

@OptIn(ExperimentalSerializationApi::class)
@Single
internal class BatchCodecV1(
    private val intentCodecRouter: IntentCodecRouter,
) : BatchCodec {

    // It may be better to make latestVersion on intentCodecRouter not private, and pass it through here
    // as otherwise the map look-up happens per encoded intent in its own methods?
    override fun encode(intents: List<SyncIntent>): ByteArray {
        require(intents.isNotEmpty()) { "Cannot serialise an empty batch" }

        val serializedEnvelopes = intents.map { intent ->
            intentCodecRouter.versionedEncode(intent)
        }

        val batchPayload = SyncBatchPayloadV1(
            size = serializedEnvelopes.size,
            envelopes = serializedEnvelopes
        )

        return ProtoBuf.encodeToByteArray(SyncBatchPayloadV1.serializer(), batchPayload)
    }

    override fun decode(bytes: ByteArray): List<SyncIntent> {
        val batchPayload =
            ProtoBuf.decodeFromByteArray(SyncBatchPayloadV1.serializer(), bytes)

        return batchPayload.envelopes.map { envelopeBytes ->
            intentCodecRouter.versionedDecode(envelopeBytes)
        }
    }

}