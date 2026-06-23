package com.mochame.sync.infrastructure.serialization.intent

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.serialization.IntentCodecRegistry
import com.mochame.sync.domain.serialization.IntentCodec
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.serialization.VersionRoutingCodec
import com.mochame.sync.contract.serialization.contextualByteDecoding
import com.mochame.sync.contract.serialization.contextualSourceDecoding
import com.mochame.sync.contract.serialization.prependVersionTo
import kotlinx.io.Buffer
import kotlinx.io.Source
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRegistry::class])
internal class DefaultIntentCodecRegistry(
    v1: IntentCodecV1,
    logger: Logger
) : VersionRoutingCodec<IntentCodec>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "IntentCodecReg")
), IntentCodecRegistry {

    override fun encode(intent: SyncIntent): ByteArray {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(intent)
        }
    }

    override fun decode(bytes: ByteArray): SyncIntent {
        return contextualByteDecoding(bytes, codecMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes)
        }
    }
}