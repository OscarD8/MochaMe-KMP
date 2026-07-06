package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.domain.serialization.IntentCodecRouter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

/*
Mixed structural versioning inside a single transport batch should be impossible on the outbound path.
 */
@ExperimentalSerializationApi
@Serializable
private data class SyncBatchPayloadV1(
    @ProtoNumber(1) val size: Int,
    @ProtoNumber(2) val envelopes: List<ByteArray> = emptyList(),
    @ProtoNumber(3) val intentSchemaVersion: Int
)

@OptIn(ExperimentalSerializationApi::class)
@Single(binds = [BatchCodec::class])
internal class BatchCodecV1(
    private val intentCodecRouter: IntentCodecRouter,
    logger: Logger
) : BatchCodec {

    private val logger =
        logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "BatCdc")

    override fun encode(intents: List<SyncIntent>): ByteArray {
        require(intents.isNotEmpty()) { "Cannot serialise an empty batch" }

        val serializedEnvelopes = intents.map { intent ->
            intentCodecRouter.routedEncode(intent)
        }

        val batchPayload = SyncBatchPayloadV1(
            size = serializedEnvelopes.size,
            envelopes = serializedEnvelopes,
            intentSchemaVersion = intentCodecRouter.latestVersion
        )

        return ProtoBuf.encodeToByteArray(SyncBatchPayloadV1.serializer(), batchPayload)
    }

    /**
     * Handles the processing of what is expected to be a payload purely comprising a list of
     * [SyncIntent] models.
     */
    override fun decode(bytes: ByteArray): List<SyncIntent> {
        val batchPayload =
            ProtoBuf.decodeFromByteArray(SyncBatchPayloadV1.serializer(), bytes)

        val decodedIntents = mutableListOf<SyncIntent>()

        for (envelopeBytes in batchPayload.envelopes) {
            try {
                decodedIntents.add(
                    intentCodecRouter.routedDecode(
                        envelopeBytes,
                        batchPayload.intentSchemaVersion
                    )
                )
            } catch (e: Exception) {
                logger.e(e) { "Corrupted intent inside batch transaction. ${e.message}" }
            }
        }
        return decodedIntents
    }

}