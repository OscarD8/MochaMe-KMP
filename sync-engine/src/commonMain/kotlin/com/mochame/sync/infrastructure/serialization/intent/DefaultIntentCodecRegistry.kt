package com.mochame.sync.infrastructure.serialization.intent

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.components.IntentCodecRegistry
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.BaseVersionedRegistry
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRegistry::class])
class DefaultSyncIntentCodecRegistry(
    v1: SyncIntentCodecV1,
    logger: Logger
) : BaseVersionedRegistry<SyncIntent, BaseSyncIntentCodec>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger,
    contextName = "SyncIntent"
), IntentCodecRegistry {

    override fun encode(intent: SyncIntent) = latestCodec.encode(intent)

    override fun decode(bytes: ByteArray) =
        routePayload(bytes) { codec, payloadBits -> codec.decode(payloadBits) }
}