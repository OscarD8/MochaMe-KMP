package com.mochame.sync.infrastructure.serialization.batch

import co.touchlab.kermit.Logger
import com.mochame.contract.exceptions.MochaException
import com.mochame.sync.domain.components.BatchCodecRegistry
import com.mochame.sync.domain.model.SyncIntent
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Single(binds = [BatchCodecRegistry::class])
class DefaultBatchCodecRegistry(
    private val v1: BatchCodecV1,
    // private val v2: SyncBatchCodecV2
    private val logger: Logger
) : BatchCodecRegistry {

    override fun encode(intents: List<SyncIntent>): ByteArray {
        // Outbound packaging always uses the absolute latest protocol version
        return v1.encode(intents)
    }

    override fun decode(bytes: ByteArray): List<SyncIntent> {
        if (bytes.isEmpty()) {
            throw MochaException.Persistent.CorruptionDetected("Empty transport package received")
        }

        return when (bytes[0]) {
            0x01.toByte() -> v1.decode(bytes)
            // 0x02.toByte() -> v2.decode(bytes)
            else -> {
                logger.e { "Unknown transport bundle version: ${bytes[0]}" }
                throw MochaException.Persistent.UnknownProtocolVersion(bytes[0])
            }
        }
    }
}