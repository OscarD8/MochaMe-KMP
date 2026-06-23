package com.mochame.sync.test.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.SyncStatus
import com.mochame.sync.infrastructure.serialization.batch.BatchCodecV1
import com.mochame.sync.infrastructure.serialization.intent.DefaultIntentCodecRegistry
import com.mochame.sync.infrastructure.serialization.intent.IntentCodecV1
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BatchCodecV1Test {

    private val logger = Logger
    private val intentCodec = IntentCodecV1(logger)
    private val intentRegistry = DefaultIntentCodecRegistry(intentCodec, logger)
    private val batchCodec = BatchCodecV1(intentRegistry, logger)

    private fun createHlc(offsetMs: Long = 0, nodeId: String = "test-node"): HLC = HLC(
        ts = HLC.APP_RELEASE_MS + offsetMs,
        count = 0,
        nodeId = nodeId
    )

    @Test
    fun testEncodeAndDecodeBatchOfIntents() {
        val intent1 = SyncIntent(
            hlc = createHlc(100, "node-1"),
            candidateKey = "candidate-1",
            module = "bio",
            model = "DailyContext",
            operation = MutationOp.UPSERT,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            createdAt = 100000000L,
            payload = byteArrayOf(10, 20),
            overflowBlobId = null
        )

        val intent2 = SyncIntent(
            hlc = createHlc(200, "node-2"),
            candidateKey = "candidate-2",
            module = "telemetry",
            model = "MoodContext",
            operation = MutationOp.UPSERT,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0,
            createdAt = 200000000L,
            payload = byteArrayOf(30, 40),
            overflowBlobId = "blob-2"
        )

        val intents = listOf(intent1, intent2)

        // Encode batch
        val batchBytes = batchCodec.encode(intents)
        assertNotNull(batchBytes)

        // Decode batch
        val source = Buffer().apply { write(batchBytes) }
        val decodedIntents = batchCodec.decode(source)

        assertEquals(2, decodedIntents.size)

        val decoded1 = decodedIntents[0]
        assertEquals(intent1.hlc, decoded1.hlc)
        assertEquals(intent1.candidateKey, decoded1.candidateKey)
        assertEquals(intent1.module, decoded1.module)
        assertEquals(intent1.model, decoded1.model)
        assertEquals(intent1.operation, decoded1.operation)
        assertEquals(SyncStatus.RECEIVED, decoded1.syncStatus)
        assertEquals(intent1.payload?.toList(), decoded1.payload?.toList())
        assertEquals(intent1.overflowBlobId, decoded1.overflowBlobId)

        val decoded2 = decodedIntents[1]
        assertEquals(intent2.hlc, decoded2.hlc)
        assertEquals(intent2.candidateKey, decoded2.candidateKey)
        assertEquals(intent2.module, decoded2.module)
        assertEquals(intent2.model, decoded2.model)
        assertEquals(intent2.operation, decoded2.operation)
        assertEquals(SyncStatus.RECEIVED, decoded2.syncStatus)
        assertEquals(intent2.payload?.toList(), decoded2.payload?.toList())
        assertEquals(intent2.overflowBlobId, decoded2.overflowBlobId)
    }
}
