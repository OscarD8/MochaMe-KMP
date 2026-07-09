package com.mochame.sync.test.fakes

import com.mochame.support.TestHlcFactory
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.serialization.IntentCodec

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
        return createTestSyncIntent(TestHlcFactory.create())
    }
}

