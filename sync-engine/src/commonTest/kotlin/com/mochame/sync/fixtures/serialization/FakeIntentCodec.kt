package com.mochame.sync.fixtures.serialization

import com.mochame.sync.domain.serialization.IntentCodec
import com.mochame.sync.fixtures.createTestSyncIntent
import com.mochame.sync.spi.models.SyncIntent
import org.koin.core.annotation.Single

@Single
class FakeIntentCodec: IntentCodec {

    companion object {
        val BYTES_PRESET = byteArrayOf(0x02, 0x02)
        val MODEL_PRESET = createTestSyncIntent(candidateKey = "FakeIntentCodec-key")
    }

    override fun encode(intent: SyncIntent): ByteArray = BYTES_PRESET

    override fun decode(bytes: ByteArray): SyncIntent {
        require(bytes.contentEquals(BYTES_PRESET)) {
            "FakeIntentCodec received unexpected bytes: ${bytes.toHexString()}"
        }

        return MODEL_PRESET
    }
}