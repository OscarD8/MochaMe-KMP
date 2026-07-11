@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.mochame.sync.infrastructure.serialization

import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.domain.serialization.BatchCodecRouter
import com.mochame.sync.domain.serialization.PayloadCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@Serializable
private data class VersionedPayload(
    @ProtoNumber(1) val batchVersion: Int,
    @ProtoNumber(2) val payload: ByteArray
)


@Single(binds = [PayloadCodec::class])
internal class DefaultPayloadCodec(
    private val batchCodecRouter: BatchCodecRouter
) : PayloadCodec {

    override fun encode(intents: List<SyncIntent>): ByteArray {
        val payload = batchCodecRouter.routedEncode(intents)
        val delta = VersionedPayload(batchCodecRouter.latestVersion, payload)

        return ProtoBuf.encodeToByteArray(VersionedPayload.serializer(), delta)
    }

    /**
     * Separates the binary payload from the version and initiates the version handling of
     * decoding the payload by calling [BatchCodecRouter], providing it the version.
     */
    override fun decode(bytes: ByteArray): List<SyncIntent> {
        val delta = ProtoBuf.decodeFromByteArray(VersionedPayload.serializer(), bytes)

        return batchCodecRouter.routedDecode(delta.payload, delta.batchVersion)
    }

}