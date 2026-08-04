package com.mochame.sync.fixtures.serialization

import com.mochame.sync.domain.serialization.BatchCodec
import com.mochame.sync.fixtures.createTestSyncIntent
import com.mochame.sync.spi.models.SyncIntent
import org.koin.core.annotation.Single


@Single
class FakeBatchCodec : BatchCodec {

    companion object {
        val BYTES_PRESET = byteArrayOf(0x04, 0x04)

        // Minimal multi-item preset using existing test model factory
        val MODEL_LIST_PRESET = listOf(
            createTestSyncIntent(candidateKey = "BATCH_ITEM_1"),
            createTestSyncIntent(candidateKey = "BATCH_ITEM_2")
        )
    }

    override fun encode(intents: List<SyncIntent>): ByteArray = BYTES_PRESET

    override fun decode(bytes: ByteArray): List<SyncIntent> {
        require(bytes.contentEquals(BYTES_PRESET)) {
            "FakeBatchCodec received unexpected bytes: ${bytes.toHexString()}"
        }

        return MODEL_LIST_PRESET
    }
}