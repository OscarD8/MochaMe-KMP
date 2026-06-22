package com.mochame.sync.infrastructure.serialization.intent

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.components.IntentCodecRegistry
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.CodecRegistry
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRegistry::class])
class DefaultIntentCodecRegistry(
    v1: IntentCodecV1,
    logger: Logger
) : CodecRegistry<VersionedIntentCodec>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger,
), IntentCodecRegistry {

    override fun encode(intent: SyncIntent) = latestCodec.encode(intent)

    override fun decode(bytes: ByteArray) =
        routePayload(bytes) { codec, payloadBits -> codec.decode(payloadBits) }
}