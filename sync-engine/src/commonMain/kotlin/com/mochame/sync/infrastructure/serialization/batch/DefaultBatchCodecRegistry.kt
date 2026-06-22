package com.mochame.sync.infrastructure.serialization.batch

import co.touchlab.kermit.Logger
import com.mochame.sync.domain.components.BatchCodecRegistry
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.infrastructure.serialization.CodecRegistry
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Single(binds = [BatchCodecRegistry::class])
class DefaultBatchCodecRegistry(
    v1: BatchCodecV1,
    logger: Logger
) : CodecRegistry<VersionedBatchCodec>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger
), BatchCodecRegistry {

    override fun encode(intents: List<SyncIntent>) = latestCodec.encode(intents)

    override fun decode(bytes: ByteArray): List<SyncIntent> =
        routePayload(bytes) { codec, bytes -> codec.decode(bytes) }
}