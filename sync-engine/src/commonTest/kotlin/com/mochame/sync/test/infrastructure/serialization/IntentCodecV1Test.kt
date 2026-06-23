package com.mochame.sync.test.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.SyncStatus
import com.mochame.sync.infrastructure.serialization.intent.IntentCodecV1
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IntentCodecV1Test {

    private val logger = Logger
    private val codec = IntentCodecV1(logger)

    private fun createHlc(offsetMs: Long = 0): HLC = HLC(
        ts = HLC.APP_RELEASE_MS + offsetMs,
        count = 0,
        nodeId = "test-node"
    )

    @Test
    fun testEncodeAndDecodeIntent() {
        val originalIntent = SyncIntent(
            hlc = createHlc(100),
            candidateKey = "candidate-123",
            module = "bio",
            model = "DailyContext",
            operation = MutationOp.UPSERT,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            createdAt = 123456789L,
            payload = byteArrayOf(1, 2, 3, 4),
            overflowBlobId = "blob-456"
        )

        // Encode
        val encodedBytes = codec.encode(originalIntent)
        assertNotNull(encodedBytes)

        // Decode
        val source = Buffer().apply { write(encodedBytes) }
        val decodedIntent = codec.decode(source)

        assertEquals(originalIntent.hlc, decodedIntent.hlc)
        assertEquals(originalIntent.candidateKey, decodedIntent.candidateKey)
        assertEquals(originalIntent.module, decodedIntent.module)
        assertEquals(originalIntent.model, decodedIntent.model)
        assertEquals(originalIntent.operation, decodedIntent.operation)
        // Note: decoded status is hardcoded to RECEIVED in IntentCodecV1.decode
        assertEquals(SyncStatus.RECEIVED, decodedIntent.syncStatus)
        assertNotNull(decodedIntent.payload)
        assertEquals(originalIntent.payload?.size, decodedIntent.payload?.size)
        assertEquals(originalIntent.payload?.toList(), decodedIntent.payload?.toList())
        assertEquals(originalIntent.overflowBlobId, decodedIntent.overflowBlobId)
    }
}
