package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.serialization.BatchCodecRouter
import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.VersionRouter
import com.mochame.sync.contract.stripAndVersion
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

    override val versionRegistry = arrayOf<BatchCodec?>(null, v1)

    /*
     question: The Java Virtual Machine maintains a permanent, pre-allocated internal array of java.lang.Byte objects on the heap for every single value from -128 to 127.
     */
    /**
     * Byte remains a primitive sitting inside this object's memory block on the heap as raw bytes, not a boxed object
     * pointer as was previously the case when using a map of <Byte, Codec>.
     * This means the CPU copies the 8 bits straight into their registers to perform operations.
     * Using a Byte means a max value of 255 (due to bit masking on the versionRegistry lookup), an Int would
     * mean a 4-byte version system on each payload component, which is not necessary.
     */
    override val latestVersion: Byte = 0x01
    private val logger =
        logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "BtcRtr")

    override fun versionEncode(intents: List<SyncIntent>): ByteArray {
        return prependVersionTo(latestVersion, logger) {
            latestCodec.encode(intents)
        }
    }

    override fun versionedDecode(bytes: ByteArray): List<SyncIntent> {
        return stripAndVersion(bytes, bytes[0], logger) { codec, cleanBytes ->
            codec.decode(cleanBytes)
        }
    }
}