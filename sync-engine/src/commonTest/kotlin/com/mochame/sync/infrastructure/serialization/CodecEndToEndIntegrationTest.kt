@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.common.TriState
import com.mochame.sync.di.codec.CodecIntegrationTestApp
import com.mochame.sync.di.codec.CodecIntegrationTestEnv
import com.mochame.sync.domain.model.deriveContext
import com.mochame.sync.fixtures.assertDecodedIntentParity
import com.mochame.sync.fixtures.serialization.FeatureEntity
import com.mochame.sync.fixtures.serialization.assertDecodeParity
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.utils.fixtures.HlcTestFactory
import kotlinx.coroutines.test.TestScope
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


private inline fun runEnv(crossinline block: suspend CodecIntegrationTestEnv.(TestScope) -> Unit) =
    runUnitEnvironment<CodecIntegrationTestEnv>(
        koinSetup = { includes(koinConfiguration<CodecIntegrationTestApp>()) },
        block = block
    )


class CodecEndToEndIntegrationTest : MochaPlatformTest() {

    // -----------------------------------------------------------
    // FEATURE ENTITY ROUND TRIP
    // -----------------------------------------------------------
    @Test
    fun should_roundTripFreshDomainEntity_across_allFiveTiers() = runEnv {
        // --- Tier 4 - 3: Feature Implementation ---
        val originalEntity = FeatureEntity()
        // Encoding at V1 (Entity -> Bytes & Context)
        val featureBytes = featureRouter.routedEncode(new = originalEntity, old = null)
        assertNotNull(featureBytes)

        // --- Tier 2 - 0: Encoding (SyncIntent -> Wire Bytes) ---
        // (FeatureBytes -> SyncIntent)
        val outboundIntent = SyncIntent(
            candidateKey = originalEntity.id,
            operation = MutationOp.UPSERT,
            createdAt = originalEntity.hlc.ts,
            hlc = originalEntity.hlc,
            overflowBlobId = null,
            payload = featureBytes,
            featureSchemaVersion = featureRouter.latestVersion,
            featureContext = FeatureContext.Type.UNRECOGNIZED_MODEL,
            syncStatus = SyncStatus.PENDING,
            retryCount = 2
        )
        // PayloadCodec -> BatchCodecRouter -> BatchCodecV1 -> IntentCodecRouter -> IntentCodecV1
        val wireBytes = payloadCodec.encode(listOf(outboundIntent))
        assertNotNull(wireBytes)

        // --- Tier 0 - 2: Decoding (Wire Bytes -> SyncIntent) ---
        val inboundIntents = payloadCodec.decode(wireBytes)
        val decodedIntent = inboundIntents[0]
        assertEquals(1, inboundIntents.size)
        assertDecodedIntentParity(outboundIntent, decodedIntent)

        val decodeContext = decodedIntent.deriveContext()

        // --- Tier 3 - 4: Feature Decoding (SyncIntent Payload -> Restored Entity) ---
        val restoredEntity = featureRouter.routedDecode(
            data = decodedIntent.payload!!,
            context = decodeContext,
            existing = null
        )
        restoredEntity.assertDecodeParity(originalEntity)
    }

    // -------------------------------------------------------------------
    // FEATURE ENTITY ROUND TRIP W/ EXISTING
    // -------------------------------------------------------------------

    @Test
    fun should_preserveDomainParity_when_decodingWithExistingLocalState() = runEnv {
        // Arrange Device A
        val originalLocalEntity = FeatureEntity(
            triStateValue = TriState.TRUE,
            textValue = "Old Local Value",
            countValue = 2,
            hlc = HlcTestFactory.create(ts = 500L),
            isDeleted = false
        )
        val updatedLocalEntity = originalLocalEntity.copy(
            triStateValue = TriState.UNSET,
            textValue = "Updated Remote Value",
            countValue = 2,
            hlc = HlcTestFactory.create(ts = 1500L),
            isDeleted = false
        )

        // Act Device A: Encode on V1
        val featureBytes =
            featureRouter.routedEncode(new = updatedLocalEntity, old = originalLocalEntity)
        val outboundIntent = SyncIntent(
            featureSchemaVersion = featureRouter.latestVersion,
            candidateKey = updatedLocalEntity.id,
            hlc = updatedLocalEntity.hlc,
            createdAt = updatedLocalEntity.hlc.ts,
            featureContext = FeatureContext.Type.UNRECOGNIZED_MODEL,
            operation = MutationOp.UPSERT,
            payload = featureBytes,
            overflowBlobId = null,
            syncStatus = SyncStatus.PENDING,
            retryCount = 0
        )
        val wireBytes = payloadCodec.encode(listOf(outboundIntent))

        // Act Device B: Decode Intent
        val inboundIntents = payloadCodec.decode(wireBytes)
        val decodedIntent = inboundIntents[0]
        assertDecodedIntentParity(outboundIntent, decodedIntent)

        val decodeContext = decodedIntent.deriveContext()

        // Act Device B: Decode Feature Data
        val decodedEntity = featureRouter.routedDecode(
            data = decodedIntent.payload!!,
            context = decodeContext,
            existing = originalLocalEntity
        )

        // Assert:
        assertEquals(originalLocalEntity.id, decodedEntity.id)
        assertEquals(updatedLocalEntity.textValue, decodedEntity.textValue)
        assertEquals(updatedLocalEntity.triStateValue, decodedEntity.triStateValue)
        assertEquals(originalLocalEntity.countValue, decodedEntity.countValue)
        assertEquals(HlcTestFactory.create(ts = 1500L), decodedEntity.hlc)
    }

    // THE ABOVE TEST PASSES BUT IDENTIFIES A BUG:
    /*
         Device A:
         existing = Z
         intent = 1 made against Z at HLC
         new = Y
         ships Intent

         Device B
         existing = Z
         intent = 2 made against Z at HLC2
         new = X
         ships Intent

         LWW
         Device B receives intent 1 representing a change from Z to Y on Device A.
         Contention locking across nodes impossible.
         It has concurrently changed Z to X and shipped compacted payload representing change from Z to X
         It rejects intent 1 as X has higher HLC.
         Device A receives intent 2 representing change from Z to X
         It decodes and compares the payload to its existing state of Y
         ... therefore Device B generated intent 2 from Z to X, whereas device A will interpret this intent from Y and forms W, which doesn't ship back to Device B.
         State Drift:
         Device A = (Z -> Y) -> (X -> W)
         Device B = (Z -> X)
     */
}
