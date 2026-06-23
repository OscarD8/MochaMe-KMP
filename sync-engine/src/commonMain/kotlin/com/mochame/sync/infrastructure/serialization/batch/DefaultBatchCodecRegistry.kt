package com.mochame.sync.infrastructure.serialization.batch

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.serialization.BatchCodecRegistry
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.serialization.VersionRoutingCodec
import com.mochame.sync.contract.serialization.contextualByteDecoding
import com.mochame.sync.contract.serialization.prependVersionTo
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Single(binds = [BatchCodecRegistry::class])
internal class DefaultBatchCodecRegistry(
    v1: BatchCodecV1,
    logger: Logger
) : VersionRoutingCodec<BatchCodec>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "BatchCodecReg")
), BatchCodecRegistry {

    override fun encode(intents: List<SyncIntent>): ByteArray {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(intents)
        }
    }

    override fun decode(bytes: ByteArray): List<SyncIntent> {
        return contextualByteDecoding(bytes, codecMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes)
        }
    }
}