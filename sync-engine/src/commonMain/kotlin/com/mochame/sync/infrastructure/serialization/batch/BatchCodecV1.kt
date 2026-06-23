package com.mochame.sync.infrastructure.serialization.batch

import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.domain.serialization.IntentCodecRegistry
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
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
    private val intentCodecRegistry: IntentCodecRegistry,
) : BatchCodec {

    override fun encode(intents: List<SyncIntent>): ByteArray {
        require(intents.isNotEmpty()) { "Cannot serialise an empty batch" }

        val serializedEnvelopes = intents.map { intent ->
            intentCodecRegistry.encode(intent)
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
            intentCodecRegistry.decode(envelopeBytes)
        }
    }

}