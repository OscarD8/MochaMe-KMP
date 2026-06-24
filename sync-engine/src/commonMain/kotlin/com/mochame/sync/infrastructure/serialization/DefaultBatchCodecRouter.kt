package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.serialization.BatchCodecRouter
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.stripAndVersionCodec
import com.mochame.sync.contract.latestCodec
import com.mochame.sync.contract.prependVersionTo
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Single(binds = [BatchCodecRouter::class])
internal class DefaultBatchCodecRouter(
    v1: BatchCodecV1,
    logger: Logger
) : VersionRouter<BatchCodec>, BatchCodecRouter {

    override val versionMap = mapOf(0x01.toByte() to v1)
    override val latestVersion: Byte = 0x01
    private val logger =
        logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "BatchCodecRouter")

    override fun versionEncode(intents: List<SyncIntent>): ByteArray {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(intents)
        }
    }

    override fun versionedDecode(bytes: ByteArray): List<SyncIntent> {
        return stripAndVersionCodec(bytes, versionMap, logger) { codec, cleanBytes ->
            codec.decode(cleanBytes)
        }
    }
}