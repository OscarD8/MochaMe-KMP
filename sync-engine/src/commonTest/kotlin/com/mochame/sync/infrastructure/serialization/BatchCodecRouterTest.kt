@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.di.codec.CodecFixtureTestApp
import com.mochame.sync.di.codec.CodecFixtureTestEnv
import com.mochame.sync.fixtures.assertSyncIntentParity
import com.mochame.sync.fixtures.createTestSyncIntent
import com.mochame.sync.fixtures.serialization.FakeBatchCodec
import com.mochame.sync.fixtures.serialization.toRouterWithVersion
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private inline fun runEnv(crossinline block: CodecFixtureTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<CodecFixtureTestEnv>(
        koinSetup = { includes(koinConfiguration<CodecFixtureTestApp>()) },
        block = block
    )

class BatchCodecRouterTest : MochaPlatformTest() {

    @Test
    fun should_roundTripPayload_when_usingDefaultVersionRouter() = runEnv {
        // Arrange
        val intents = listOf(createTestSyncIntent())

        // Act
        val bytes = batchRouter.routedEncode(intents)
        val decoded = batchRouter.routedDecode(bytes, version = batchRouter.latestVersion)

        // Assert
        assertEquals(1, decoded.size)
        assertSyncIntentParity(intents[0], decoded[0])
    }

    @Test
    fun should_delegateRoutedEncodeToLatestVersion_when_multiVersionRouterConfigured() = runEnv {
        // Arrange: Router registry [null, realV1, fakeV2], latestVersion = 2
        val multiVersionRouter = realBatchCodec.toRouterWithVersion(v2 = fakeBatchCodec, logger)

        // Act: routedEncode must select latestVersion (V2)
        val bytes = multiVersionRouter.routedEncode(emptyList())

        // Assert
        assertContentEquals(FakeBatchCodec.BYTES_PRESET, bytes)
    }

    @Test
    fun should_dispatchRoutedDecodeToCorrectVersionCodec_when_validVersionProvided() = runEnv {
        // Arrange
        val multiVersionRouter = realBatchCodec.toRouterWithVersion(v2 = fakeBatchCodec, logger)
        val v1Intent = createTestSyncIntent(candidateKey = "batch-v1-key")
        val v1Bytes = realBatchCodec.encode(listOf(v1Intent))

        // Act
        val decodedV1 = multiVersionRouter.routedDecode(v1Bytes, version = 1)
        val decodedV2 = multiVersionRouter.routedDecode(FakeBatchCodec.BYTES_PRESET, version = 2)

        // Assert
        assertEquals(1, decodedV1.size)
        assertSyncIntentParity(v1Intent, decodedV1[0])

        assertEquals(2, decodedV2.size)
        assertEquals("BATCH_ITEM_1", decodedV2[0].candidateKey)
        assertEquals("BATCH_ITEM_2", decodedV2[1].candidateKey)
    }

    @Test
    fun should_throwUnknownProtocolVersion_when_versionIsUnregisteredOrOutOfBounds() = runEnv {
        // Arrange
        val multiVersionRouter = realBatchCodec.toRouterWithVersion(v2 = fakeBatchCodec, logger)
        val sampleBytes = FakeBatchCodec.BYTES_PRESET

        // Assert: Verify all out-of-bounds index lookups throw domain versioning exception
        listOf(-1, 0, 99).forEach { invalidVersion ->
            assertFailsWith<MochaException.Persistent.UnknownProtocolVersion> {
                multiVersionRouter.routedDecode(sampleBytes, version = invalidVersion)
            }
        }
    }
}