package com.mochame.sync.infrastructure.codec.intent

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.domain.components.SyncIntentCodecRegistry
import com.mochame.sync.domain.model.SyncIntent
import org.koin.core.annotation.Single

@Single(binds = [SyncIntentCodecRegistry::class])
class DefaultSyncIntentCodecRegistry(
    private val v1: SyncIntentCodecV1,
    // private val v2: SyncCodecV2 - future
    logger: Logger
) : SyncIntentCodecRegistry {

    private val logger =
        logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "SyncCodecRegistry")

    override fun encode(intent: SyncIntent): ByteArray {
        // Always encode with the latest protocol version.
        // When adding V2: change this line and add V2 to the when block in decode.
        return v1.encode(intent)
    }

    override fun decode(bytes: ByteArray): SyncIntent {
        if (bytes.isEmpty()) throw MochaException.Persistent.CorruptionDetected(
            "Empty sync envelope received"
        )
        return when (bytes[0]) {
            0x01.toByte() -> v1.decode(bytes)
            else -> {
                logger.e { "Unknown sync envelope version: ${bytes[0]}" }
                throw MochaException.Persistent.UnknownProtocolVersion(bytes[0])
            }
        }
    }

}