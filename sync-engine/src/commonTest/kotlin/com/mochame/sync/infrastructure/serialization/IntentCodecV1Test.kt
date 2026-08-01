package com.mochame.sync.infrastructure.serialization

import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.di.codec.CodecTestApp
import com.mochame.sync.fixtures.createTestSyncIntent
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.utils.fixtures.HlcTestFactory
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull


// -------------------------------------------------------------------
// SUT ENVIRONMENT
// -------------------------------------------------------------------
private inline fun runEnv(crossinline block: suspend IntentCodecV1.(TestScope) -> Unit) =
    runUnitEnvironment(
        koinSetup = { includes(koinConfiguration<CodecTestApp>()) },
        block = block
    )

class IntentCodecV1Test : MochaPlatformTest() {

    // -------------------------------------------------------------------
    // PAYLOAD PARITY
    // -------------------------------------------------------------------

    @Test
    fun should_preserve_populated_payload_and_created_at_across_encode_decode() = runEnv {
        // Given
        val expectedHlc = HlcTestFactory.create(ts = 1740787200000L, count = 2)
        val originalPayload = byteArrayOf(0x01, 0x02, 0x0F, 0x7F, -0x80)
        val originalCreatedAt = 1740787000000L

        val originalIntent = createTestSyncIntent(
            hlc = expectedHlc,
            payload = originalPayload,
            createdAt = originalCreatedAt,
            overflowBlobId = null
        )

        // When
        val encodedBytes = encode(originalIntent)
        val decodedIntent = decode(encodedBytes)

        // Then
        assertSyncIntentParity(originalIntent, decodedIntent)
        assertContentEquals(originalPayload, decodedIntent.payload)
    }

    @Test
    fun should_differentiate_and_preserve_empty_byte_array_payload() = runEnv {
        // Given
        val emptyPayload = byteArrayOf()
        val originalIntent = createTestSyncIntent(
            payload = emptyPayload,
            overflowBlobId = null
        )

        // When
        val encodedBytes = encode(originalIntent)
        val decodedIntent = decode(encodedBytes)

        // Then
        assertSyncIntentParity(originalIntent, decodedIntent)
        assertEquals(0, decodedIntent.payload?.size)
        assertContentEquals(emptyPayload, decodedIntent.payload)
    }

    @Test
    fun should_preserve_null_payload_and_retain_overflow_blob_id() = runEnv {
        // Given
        val originalIntent = createTestSyncIntent(
            payload = null,
            overflowBlobId = "blob-overflow-uuid-9981"
        )

        // When
        val encodedBytes = encode(originalIntent)
        val decodedIntent = decode(encodedBytes)

        // Then
        assertSyncIntentParity(originalIntent, decodedIntent)
        assertNull(decodedIntent.payload)
        assertEquals("blob-overflow-uuid-9981", decodedIntent.overflowBlobId)
    }

    @Test
    fun should_handle_large_payload_1mb_without_truncation_or_corruption() = runEnv {
        // Given
        val largePayload = ByteArray(1_024 * 1_024) { (it % 256).toByte() }
        val originalIntent = createTestSyncIntent(
            payload = largePayload,
            overflowBlobId = null
        )

        // When
        val encodedBytes = encode(originalIntent)
        val decodedIntent = decode(encodedBytes)

        // Then
        assertEquals(largePayload.size, decodedIntent.payload?.size)
        assertContentEquals(largePayload, decodedIntent.payload)
        assertSyncIntentParity(originalIntent, decodedIntent)
    }

    // -------------------------------------------------------------------
    // SYNCINTENT FIELD PARITY
    // -------------------------------------------------------------------


    // --- HELPERS ---
    private fun assertSyncIntentParity(expected: SyncIntent, actual: SyncIntent) {
        assertEquals(expected.featureSchemaVersion, actual.featureSchemaVersion)
        assertEquals(expected.hlc, actual.hlc)
        assertEquals(expected.candidateKey, actual.candidateKey)

        assertEquals(
            expected.featureContext.featureName,
            actual.featureContext.featureName
        )
        assertEquals(expected.featureContext.modelName, actual.featureContext.modelName)

        assertEquals(expected.operation, actual.operation)
        assertEquals(expected.overflowBlobId, actual.overflowBlobId)

        assertEquals(
            expected.createdAt,
            actual.createdAt,
            "createdAt must match original intent value"
        )

        assertEquals(
            SyncStatus.RECEIVED,
            actual.syncStatus,
            "syncStatus must reset to RECEIVED upon decode"
        )
        assertEquals(0, actual.retryCount, "retryCount must reset to 0 upon decode")
    }
}