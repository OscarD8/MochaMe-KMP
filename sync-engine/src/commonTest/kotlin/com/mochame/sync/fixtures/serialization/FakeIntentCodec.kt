package com.mochame.sync.fixtures.serialization

import com.mochame.utils.fixtures.HlcTestFactory
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.domain.serialization.IntentCodec
import com.mochame.sync.fixtures.createTestSyncIntent

// Preliminary example
private class FakeIntentCodec(
    private val stubbedBytes: ByteArray = byteArrayOf(0x99.toByte())
) : IntentCodec {

    var encodeCalled = false
    var decodeCalledWith: ByteArray? = null

    override fun encode(intent: SyncIntent): ByteArray {
        encodeCalled = true
        return stubbedBytes
    }

    override fun decode(bytes: ByteArray): SyncIntent {
        decodeCalledWith = bytes
        return createTestSyncIntent(HlcTestFactory.create())
    }
}

