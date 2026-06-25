package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.serialization.IntentCodecRouter
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.stripAndVersion
import com.mochame.sync.contract.latestCodec
import com.mochame.sync.contract.prependVersionTo
import com.mochame.sync.domain.serialization.IntentCodec
import org.koin.core.annotation.Single

@Single(binds = [IntentCodecRouter::class])
internal class DefaultIntentCodecRouter(
    v1: IntentCodecV1,
    logger: Logger
) : VersionRouter<IntentCodec>, IntentCodecRouter {

    override val versionMap: Map<Byte, IntentCodec> = mapOf(0x01.toByte() to v1)
    override val latestVersion: Byte = 0x01
    private val logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "IntRtr")


    override fun versionedEncode(intent: SyncIntent): ByteArray {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(intent)
        }
    }

    override fun versionedDecode(bytes: ByteArray): SyncIntent {
        return stripAndVersion(bytes, versionMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes)
        }
    }
}