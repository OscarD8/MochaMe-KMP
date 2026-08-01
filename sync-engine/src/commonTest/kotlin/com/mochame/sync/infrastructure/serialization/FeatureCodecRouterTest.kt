package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.exceptions.MochaException
import com.mochame.sync.di.codec.CodecTestApp
import com.mochame.sync.fixtures.serialization.FeatureCodecV2
import com.mochame.sync.fixtures.serialization.FeatureEntity
import com.mochame.sync.fixtures.serialization.FeatureCodecRouter
import com.mochame.sync.fixtures.serialization.deriveContext
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodecRouter
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.SerializationException
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull


// -------------------------------------------------------------------
// SUT ENVIRONMENT
// -------------------------------------------------------------------
private inline fun runEnv(crossinline block: suspend FeatureCodecRouter.(TestScope) -> Unit) =
    runUnitEnvironment(
        koinSetup = { includes(koinConfiguration<CodecTestApp>()) },
        block = block
    )

class FeatureCodecRouterTest : MochaPlatformTest() {

    // -------------------------------------------------------------------
    // DISPATCH & ROUTING
    // -------------------------------------------------------------------

    @Test
    fun should_delegateToLatestVersion_on_routedEncode() = runEnv {
        val entity = FeatureEntity()

        // routedEncode must always use latestCodec (V2)
        val encodedBytes = routedEncode(new = entity, old = null)

        assertNotNull(encodedBytes)
        assertContentEquals(
            FeatureCodecV2.BYTES_PRESET,
            encodedBytes,
            "routedEncode must delegate strictly to V2 codec bytes"
        )
    }

    @Test
    fun should_delegateToLatestVersion_on_routedSummarize() = runEnv {
        val entity = FeatureEntity()

        // routedSummarize must always use latestCodec (V2)
        val summary = routedSummarize(new = entity, old = null)

        assertEquals(
            FeatureCodecV2.SUMMARIZE_PRESET,
            summary,
            "routedSummarize must delegate strictly to V2 codec"
        )
    }

    @Test
    fun should_delegateToV1Codec_on_routedDecode_when_schemaVersionIs1() = runEnv {
        val legacyEntity = FeatureEntity()
        val contextV1 = legacyEntity.deriveContext()
        val v1Bytes = v1.encode(new = legacyEntity, old = null)!!

        val decoded = routedDecode(data = v1Bytes, context = contextV1, existing = null)

        assertEquals(legacyEntity.id, decoded.id)
        assertEquals(legacyEntity.textValue, decoded.textValue)
        assertEquals(legacyEntity.hlc, decoded.hlc)
        assertFalse(decoded.isDeleted)
    }

    @Test
    fun should_delegateToV2Codec_on_routedDecode_when_schemaVersionIs2() = runEnv {
        val entity = FeatureCodecV2.MODEL_PRESET
        val contextV2 = entity.deriveContext(schemaVersion = 2)

        val decoded = routedDecode(
            data = FeatureCodecV2.BYTES_PRESET,
            context = contextV2,
            existing = null
        )

        // If v1 was routed, Serialization exception would be thrown
        assertEquals(entity.hlc, decoded.hlc)
    }

    @Test
    fun should_throwSerializationException_on_routedDecode_when_schemaVersionIs1AndBytesAre2() =
        runEnv {
            val entity = FeatureCodecV2.MODEL_PRESET
            val contextV2 = entity.deriveContext()

            assertFailsWith<SerializationException> {
                routedDecode(
                    data = FeatureCodecV2.BYTES_PRESET,
                    context = contextV2,
                    existing = null
                )
            }
        }

    @Test
    fun should_routeReconstructSummary_according_to_schemaVersion() = runEnv {
        // Given
        val v1Entity = FeatureEntity()
        val v1Context = v1Entity.deriveContext()
        val v1Bytes = v1.encode(new = v1Entity, old = null)!!

        val v2Entity = FeatureCodecV2.MODEL_PRESET
        val v2Context = v2Entity.deriveContext(schemaVersion = 2)

        // When
        val summaryV1 = routedReconstructSummary(v1Bytes, v1Context)
        val summaryV2 = routedReconstructSummary(FeatureCodecV2.BYTES_PRESET, v2Context)

        assertEquals(
            "OP:UPSERT [3,4,5]",
            summaryV1,
            "routedReconstructSummary must dispatch to V1 peeking logic"
        )
        assertEquals(
            FeatureCodecV2.RECONSTRUCT_PRESET,
            summaryV2,
            "routedReconstructSummary must dispatch to V2 peeking logic"
        )
    }

    // -------------------------------------------------------------------
    // VERSION REGISTRY & PROTOCOL EXCEPTIONS
    // -------------------------------------------------------------------

    @Test
    fun should_throwUnknownProtocolVersion_when_schemaVersionIsIndexZero() = runEnv {
        val entity = FeatureEntity()
        val context = entity.deriveContext(schemaVersion = 0)

        val exception =
            assertFailsWith<MochaException.Persistent.UnknownProtocolVersion> {
                routedDecode(
                    data = FeatureCodecV2.BYTES_PRESET,
                    context = context,
                    existing = null
                )
            }

        assertEquals(
            0,
            exception.version,
            "Exception payload must report requested version 0"
        )
    }

    @Test
    fun should_throwUnknownProtocolVersion_when_schemaVersionIsOutOfBounds() = runEnv {
        val entity = FeatureEntity()
        val context = entity.deriveContext(schemaVersion = 3)

        val exception =
            assertFailsWith<MochaException.Persistent.UnknownProtocolVersion> {
                routedDecode(
                    data = FeatureCodecV2.BYTES_PRESET,
                    context = context,
                    existing = null
                )
            }

        assertEquals(
            3,
            exception.version,
            "Exception payload must report requested out-of-bounds version 3"
        )
    }

    @Test
    fun should_throwUnknownProtocolVersion_when_schemaVersionIsNegative() = runEnv {
        val entity = FeatureEntity()
        val context = entity.deriveContext(schemaVersion = -1)

        val exception =
            assertFailsWith<MochaException.Persistent.UnknownProtocolVersion> {
                routedDecode(
                    data = FeatureCodecV2.BYTES_PRESET,
                    context = context,
                    existing = null
                )
            }

        assertEquals(
            -1,
            exception.version,
            "Negative versions must throw UnknownProtocolVersion with version -1"
        )
    }

    @Test
    fun should_throwUnknownProtocolVersion_when_registryHasGappedNullVersion() = runEnv {
        val gappedRouter = object : BaseFeatureCodecRouter<FeatureEntity>(
            latestVersion = 2,
            versionRegistry = arrayOf(null, null, null),
            logger = Logger.withTag("TestGappedRouter")
        ) {}

        val entity = FeatureEntity()
        val context = entity.deriveContext()

        val exception =
            assertFailsWith<MochaException.Persistent.UnknownProtocolVersion> {
                gappedRouter.routedDecode(
                    data = FeatureCodecV2.BYTES_PRESET,
                    context = context,
                    existing = null
                )
            }

        assertEquals(
            1,
            exception.version,
            "Null registry slots must fail gracefully with version 1"
        )
    }
}