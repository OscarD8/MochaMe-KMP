package com.mochame.sync.internal.fixtures.serialization

import com.mochame.sync.internal.fixtures.createTestSyncIntent
import com.mochame.sync.spi.infrastructure.serialization.IntentCodec
import com.mochame.sync.spi.models.SyncIntent
import org.koin.core.annotation.Single

@Single
class FakeIntentCodec: IntentCodec {

    companion object {
        val BYTES_PRESET = byteArrayOf(0x02, 0x02)
        val MODEL_PRESET = createTestSyncIntent(candidateKey = 5L)
    }

    override fun encode(intent: SyncIntent): ByteArray = BYTES_PRESET

    override fun decode(bytes: ByteArray): SyncIntent {
        require(bytes.contentEquals(BYTES_PRESET)) {
            "FakeIntentCodec received unexpected bytes: ${bytes.toHexString()}"
        }

        return MODEL_PRESET
    }
}