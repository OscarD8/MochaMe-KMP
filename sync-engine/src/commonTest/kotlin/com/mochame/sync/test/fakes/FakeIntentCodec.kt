package com.mochame.sync.test.fakes

import co.touchlab.kermit.Logger
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.infrastructure.serialization.deprecated.VersionedIntentCodec

// Preliminary example
private class FakeIntentCodec(
    logger: Logger,
    private val stubbedBytes: ByteArray = byteArrayOf(0x99.toByte())
) : VersionedIntentCodec(0x01, logger) {

    var encodeCalled = false
    var decodeCalledWith: ByteArray? = null

    override fun encodePayload(intent: SyncIntent): ByteArray {
        encodeCalled = true
        return stubbedBytes
    }

    override fun decodePayload(bits: ByteArray): SyncIntent {
        decodeCalledWith = bits
        return createTestSyncIntent()
    }
}

