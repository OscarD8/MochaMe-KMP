package com.mochame.bio.test.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.bio.infrastructure.BioCodecV1
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.models.HLC
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BioCodecV1Test {

    private val logger = Logger
    private val codec = BioCodecV1(logger)

    private fun createHlc(offsetMs: Long = 0): HLC = HLC(
        ts = HLC.APP_RELEASE_MS + offsetMs,
        count = 0,
        nodeId = "test-node"
    )

    @Test
    fun testEncodeAndDecodeDailyContext() {
        val dailyContext = DailyContext(
            id = "19234",
            hlc = createHlc(100),
            lastModified = 123456789L,
            epochDay = 19234L,
            sleepHours = 7.5,
            readinessScore = 85,
            isNapped = true,
            isDeleted = false
        )

        // Encode
        val encodedBytes = codec.encode(dailyContext, old = null)
        assertNotNull(encodedBytes)

        // Decode context
        val context = DecodeContext(
            id = dailyContext.id,
            hlc = dailyContext.hlc,
            op = MutationOp.UPSERT,
            lastModified = dailyContext.lastModified
        )

        val source = Buffer().apply { write(encodedBytes) }
        val decoded = codec.decode(source, context)

        assertEquals(dailyContext.id, decoded.id)
        assertEquals(dailyContext.hlc, decoded.hlc)
        assertEquals(dailyContext.lastModified, decoded.lastModified)
        assertEquals(dailyContext.epochDay, decoded.epochDay)
        assertEquals(dailyContext.sleepHours, decoded.sleepHours)
        assertEquals(dailyContext.readinessScore, decoded.readinessScore)
        assertEquals(dailyContext.isNapped, decoded.isNapped)
        assertEquals(dailyContext.isDeleted, decoded.isDeleted)
    }

    @Test
    fun testReconstructSummaryIsNonDestructive() {
        val dailyContext = DailyContext(
            id = "19234",
            hlc = createHlc(100),
            lastModified = 123456789L,
            epochDay = 19234L,
            sleepHours = 8.0,
            readinessScore = 90,
            isNapped = false,
            isDeleted = false
        )

        val encodedBytes = codec.encode(dailyContext, old = null)
        assertNotNull(encodedBytes)

        val source = Buffer().apply { write(encodedBytes) }

        // Call reconstructSummary (should peek and not consume bytes)
        val summary = codec.reconstructSummary(source)
        assertTrue(summary.contains("OP:UPSERT_V1"))

        // Verify the source has not been consumed and we can still decode it
        val context = DecodeContext(
            id = dailyContext.id,
            hlc = dailyContext.hlc,
            op = MutationOp.UPSERT,
            lastModified = dailyContext.lastModified
        )
        val decoded = codec.decode(source, context)

        assertEquals(dailyContext.sleepHours, decoded.sleepHours)
        assertEquals(dailyContext.readinessScore, decoded.readinessScore)
        assertEquals(dailyContext.isNapped, decoded.isNapped)
    }

    @Test
    fun testSummarizeMatchesExpectedPattern() {
        val old = DailyContext(
            id = "19234",
            hlc = createHlc(50),
            lastModified = 123456000L,
            epochDay = 19234L,
            sleepHours = 7.0,
            readinessScore = 80,
            isNapped = false,
            isDeleted = false
        )

        val new = old.copy(
            hlc = createHlc(100),
            sleepHours = 7.5, // Changed
            readinessScore = 80, // Unchanged
            isNapped = true // Changed
        )

        val summary = codec.summarize(new, old)
        // Fields sleepHours (tag 2) and isNapped (tag 4) changed.
        assertEquals("OP:UPSERT_V1 [2,4]", summary)
    }
}
