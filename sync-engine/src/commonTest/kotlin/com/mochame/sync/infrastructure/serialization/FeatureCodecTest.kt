@file:OptIn(ExperimentalKermitApi::class)

package com.mochame.sync.infrastructure.serialization

import co.touchlab.kermit.ExperimentalKermitApi
import com.mochame.support.MochaPlatformTest
import com.mochame.support.runUnitEnvironment
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.common.TriState
import com.mochame.sync.di.codec.CodecTestApp
import com.mochame.sync.fixtures.serialization.FeatureCodecV1
import com.mochame.sync.fixtures.serialization.FeatureEntity
import com.mochame.sync.fixtures.serialization.FeatureEntityDeltaV1
import com.mochame.sync.fixtures.serialization.assertDecodeParity
import com.mochame.sync.fixtures.serialization.deriveContext
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.utils.fixtures.TestHlcFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue


// -----------------------------------------------------------
// SUT ENVIRONMENT
// -----------------------------------------------------------
private inline fun runEnv(crossinline block: suspend FeatureCodecV1.(TestScope) -> Unit) =
    runUnitEnvironment<FeatureCodecV1>(
        koinSetup = { includes(koinConfiguration<CodecTestApp>()) },
        block = block
    )

@ExperimentalSerializationApi
class FeatureCodecTest : MochaPlatformTest() {

    // -------------------------------------------------------------------
    // DELTA ENCODING TESTS
    // -------------------------------------------------------------------

    @Test
    fun should_encodeFullPayload_when_insertingNewEntity() = runEnv {
        val newEntity = FeatureEntity()

        val bytes = encode(new = newEntity, old = null)
        assertNotNull(bytes)
        val delta = ProtoBuf.decodeFromByteArray(FeatureEntityDeltaV1.serializer(), bytes)

        assertEquals(newEntity.id, delta.id)
        assertEquals(newEntity.triStateValue, delta.triStateValue)
        assertEquals(newEntity.textValue, delta.textValue)
        assertEquals(newEntity.countValue, delta.countValue)
        assertNull(delta.isDeleted)
    }

    @Test
    fun should_encodeSparseDelta_when_partiallyUpdatingFields() = runEnv {
        // Given
        val oldEntity = FeatureEntity()
        val newEntity = oldEntity.copy(countValue = 20)

        // When Encode
        val fullBytes = encode(new = oldEntity, old = null)
        val sparseBytes = encode(new = newEntity, old = oldEntity)
        assertNotNull(fullBytes)
        assertNotNull(sparseBytes)
        assertTrue(
            sparseBytes.size < fullBytes.size,
            "Sparse delta must be smaller than full payload"
        )

        // When Decode
        val delta =
            ProtoBuf.decodeFromByteArray(FeatureEntityDeltaV1.serializer(), sparseBytes)
        assertEquals(oldEntity.id, delta.id)
        assertNull(delta.isDeleted, "Unchanged deletion state must be omitted")
        assertNull(delta.triStateValue, "Unchanged triStateValue must be omitted")
        assertNull(delta.textValue, "Unchanged textValue must be omitted")
        assertEquals(20, delta.countValue)
    }

    @Test
    fun should_returnNull_when_encodingNoOpUpdate() = runEnv {
        val entity = FeatureEntity()

        val bytes = encode(new = entity, old = entity)
        assertNull(
            bytes,
            "Identical entity states must return null to prevent empty outbox writes"
        )
    }

    @Test
    fun should_encodeTombstoneOnly_when_entityIsDeleted() = runEnv {
        val oldEntity = FeatureEntity()
        val deletedEntity = oldEntity.copy(
            textValue = "modified but deleted",
            countValue = 999,
            isDeleted = true
        )

        val bytes = encode(new = deletedEntity, old = oldEntity)
        assertNotNull(bytes)

        val delta = ProtoBuf.decodeFromByteArray(FeatureEntityDeltaV1.serializer(), bytes)
        assertEquals(oldEntity.id, delta.id)
        assertEquals(true, delta.isDeleted)
        assertNull(delta.triStateValue, "Payload fields must be stripped on delete")
        assertNull(delta.textValue, "Payload fields must be stripped on delete")
        assertNull(delta.countValue, "Payload fields must be stripped on delete")
    }

    @Test
    fun should_preserveTriStateTransitions_on_deltaWire() = runEnv {
        val baseEntity = FeatureEntity(triStateValue = TriState.TRUE)
        val falseEntity = baseEntity.copy(triStateValue = TriState.FALSE)

        // When Encode/Decode on False change
        val falseBytes = encode(new = falseEntity, old = baseEntity)!!
        val falseDelta =
            ProtoBuf.decodeFromByteArray(FeatureEntityDeltaV1.serializer(), falseBytes)
        assertEquals(TriState.FALSE, falseDelta.triStateValue)

        // When Encode/Decode on Unset change
        val unsetEntity = falseEntity.copy(triStateValue = TriState.UNSET)
        val unsetBytes = encode(new = unsetEntity, old = falseEntity)!!
        val unsetDelta =
            ProtoBuf.decodeFromByteArray(FeatureEntityDeltaV1.serializer(), unsetBytes)
        assertEquals(TriState.UNSET, unsetDelta.triStateValue)
    }

    // -------------------------------------------------------------------
    // DECODING & HYDRATION TESTS
    // -------------------------------------------------------------------

    @Test
    fun should_hydrateFullEntityAndInternalizeFieldLWW_when_existingStateIsNull() = runEnv {
        // Given
        val insertEntity = FeatureEntity(
            id = 1000L,
            triStateValue = TriState.TRUE,
            textValue = "initial payload",
            countValue = 42,
            fieldHlcs = ByteArray(0)
        )
        val inboundBytes = encode(new = insertEntity, old = null)!!
        val context = insertEntity.deriveContext(op = MutationOp.UPSERT)

        // When
        val hydrated = decode(bytes = inboundBytes, context = context, existing = null)

        // Then: Values match and fieldHlcs is populated with an opaque non-empty blob
        hydrated.assertDecodeParity(insertEntity)
        assertTrue(
            hydrated.fieldHlcs.isNotEmpty(),
            "Codec must generate an opaque fieldHlcs tracking blob"
        )
    }

    @Test
    fun should_mergeSparseDeltaAndPreserveUnchangedFields_when_incomingHlcIsNewer() = runEnv {
        // Given:  Hydrate initial entity (gets real underlying binary blob)
        val hlcs = TestHlcFactory.chronologicalSequence(2)
        val fieldHlclessEntity =
            FeatureEntity(id = 1000L, hlc = hlcs[0], countValue = 10, textValue = "keep me")
        val initialEntity = decode(
            bytes = encode(new = fieldHlclessEntity, old = null)!!,
            context = fieldHlclessEntity.deriveContext(),
            existing = null
        )

        // Given: Remote delta mutates ONLY countValue at a newer HLC (hlcs[1] > hlcs[0])
        val updatedSparseEntity = initialEntity.copy(countValue = 50, hlc = hlcs[1])
        val sparseBytes = encode(new = updatedSparseEntity, old = initialEntity)!!
        val remoteContext = updatedSparseEntity.deriveContext()

        // When: Decode sparse delta over existing entity
        val merged = decode(bytes = sparseBytes, context = remoteContext, existing = initialEntity)

        // Then: Mutated field updates while omitted field remains intact
        assertEquals(50, merged.countValue, "Mutated countValue must update to 50")
        assertEquals("keep me", merged.textValue, "Omitted textValue must remain intact")
        assertNotSame(
            merged.fieldHlcs,
            initialEntity.fieldHlcs,
            "Binary Blob should adjust for changed causality."
        )
    }

    @Test
    fun should_rejectSparseFieldUpdate_when_incomingHlcIsOlderThanLocalField() = runEnv {
        // Given: Local entity hydrated at newer clock (hlcs[1])
        val hlcs = TestHlcFactory.chronologicalSequence(2)
        val olderHlc = hlcs[0]
        val newerHlc = hlcs[1]

        val existingEntity = decode(
            bytes = encode(new = FeatureEntity(id = 1000L, countValue = 100), old = null)!!,
            context = DecodeContext(
                candidateKey = 1000L,
                hlc = newerHlc,
                op = MutationOp.UPSERT,
                featureSchemaVersion = 1
            ),
            existing = null
        )

        // Given: Inbound stale delta attempting to overwrite countValue with older clock (olderHlc)
        val staleSparseBytes = encode(
            new = existingEntity.copy(countValue = 20, hlc = olderHlc),
            old = existingEntity
        )!!
        val staleContext = DecodeContext(
            candidateKey = existingEntity.id,
            hlc = olderHlc,
            op = MutationOp.UPSERT,
            featureSchemaVersion = 1
        )

        // When: Decode stale update against newer existing entity
        val decoded =
            decode(bytes = staleSparseBytes, context = staleContext, existing = existingEntity)

        // Then: LWW rejects the stale value; existing local value (100) is preserved
        assertEquals(
            100,
            decoded.countValue,
            "Stale countValue (20) must be rejected by LWW in favor of local (100)"
        )
        assertSame(
            decoded.fieldHlcs,
            existingEntity.fieldHlcs,
            "Binary Blob should adjust for changed causality."
        )
    }

    @Test
    fun should_trackDeletionAndEnforceRestoreLww_when_processingDeletionLifecycle() = runEnv {
        val hlcs = TestHlcFactory.chronologicalSequence(4)
        val hlcInsert = hlcs[0]
        val hlcDelete = hlcs[1]
        val hlcRestore = hlcs[3]
        val initialEntity = FeatureEntity(id = 1000L, hlc = hlcInsert, textValue = "alive")


        // 1. Initial Insert
        val decodedInitial = decode(
            bytes = encode(new = initialEntity, old = null)!!,
            context = initialEntity.deriveContext(),
            existing = null
        )
        assertFalse(decodedInitial.isDeleted)

        // 2. Delete (hlcDelete > hlcInsert)
        val deletedEntity = decodedInitial.copy(isDeleted = true, hlc = hlcDelete)
        val deleteBytes = encode(new = deletedEntity, old = decodedInitial)!!
        val decodedDeleted = decode(
            bytes = deleteBytes,
            context = deletedEntity.deriveContext(),
            existing = decodedInitial
        )
        assertTrue(decodedDeleted.isDeleted, "Entity must be marked deleted")

        // 3. Stale Upsert Attempt (hlcInsert < hlcDelete)
        val staleEntity = decodedInitial.copy(textValue = "stale edit")
        val persistedDelete = decode(
            bytes = encode(new = staleEntity, old = null)!!,
            context = staleEntity.deriveContext(),
            existing = decodedDeleted
        )
        assertTrue(persistedDelete.isDeleted, "Stale upsert must not restore deleted entity")

        // 4. Valid Restore Upsert (hlcRestore > hlcDelete)
        val restoredEntity = decodedInitial.copy(textValue = "hello again", hlc = hlcRestore)
        val restoreBytes = encode(new = restoredEntity, old = null)!!

        val finalEntity = decode(
            bytes = restoreBytes,
            context = restoredEntity.deriveContext(),
            existing = persistedDelete
        )
        assertFalse(finalEntity.isDeleted, "Newer update must resurrect entity")
        assertEquals("hello again", finalEntity.textValue)
        assertNotSame(persistedDelete.fieldHlcs, finalEntity.fieldHlcs)
    }

    @Test
    fun should_overwriteExistingField_when_deltaContainsEmptyString() = runEnv {
        val existingEntity = FeatureEntity()

        // When (Device A: User clears field)
        val clearedEntity = existingEntity.copy(textValue = "")
        val bytes = encode(new = clearedEntity, old = existingEntity)!!

        // When (Device B: Decode)
        val context = clearedEntity.deriveContext()
        val decoded = decode(bytes = bytes, context = context, existing = existingEntity)

        assertEquals(
            "",
            decoded.textValue,
            "Empty string delta must overwrite existing non-empty value and be included in delta"
        )
        assertEquals(existingEntity.id, decoded.id)
    }

    @Test
    fun should_applyContextTimestamps_during_hydration() = runEnv {
        val hlcs = TestHlcFactory.chronologicalSequence(2)
        val existingEntity = FeatureEntity(hlc = hlcs[0])

        // When (Device A: Update and Encode)
        val updatedEntity = existingEntity.copy(hlc = hlcs[1], countValue = 6)
        val bytes = encode(new = updatedEntity, old = existingEntity)!!

        // When (Device B: Decode)
        val remoteContext = DecodeContext(
            candidateKey = updatedEntity.id,
            hlc = updatedEntity.hlc,
            op = MutationOp.UPSERT,
            featureSchemaVersion = 1
        )
        val hydrated = decode(
            bytes = bytes,
            context = remoteContext,
            existing = existingEntity
        )

        assertEquals(
            hlcs[1],
            hydrated.hlc,
            "Entity HLC must take inbound DecodeContext HLC"
        )
        assertEquals(
            hlcs[1].ts,
            hydrated.lastModified,
            "Entity lastModified must take DecodeContext timestamp"
        )
    }

    @Test
    fun should_throwSerializationException_when_decodingCorruptBytes() = runEnv {
        val corruptBytes = byteArrayOf(0x00, 0x80.toByte(), 0xFF.toByte())
        val context = DecodeContext(
            candidateKey = 1000L,
            hlc = TestHlcFactory.create(),
            op = MutationOp.UPSERT,
            featureSchemaVersion = 1
        )

        assertFailsWith<SerializationException> {
            decode(bytes = corruptBytes, context = context, existing = null)
        }
    }

    // -------------------------------------------------------------------
    // SUMMARY PARITY (IN-MEMORY vs BINARY PEEKING)
    // -------------------------------------------------------------------

    @Test
    fun should_maintainSummaryParity_on_fullInsert() = runEnv {
        val newEntity = FeatureEntity()
        val bytes = encode(new = newEntity, old = null)
        val changedTags = computeChangedTags(newEntity, null)

        // When
        val inMemorySummary = summarize(MutationOp.UPSERT, changedTags)
        val binarySummary = reconstructSummary(bytes!!)

        val (inMemOp, inMemTags) = parseSummary(inMemorySummary)
        val (binOp, binTags) = parseSummary(binarySummary)

        assertEquals(MutationOp.UPSERT.name, inMemOp, "Opcode must be UPSERT")
        assertEquals(inMemOp, binOp, "In-memory opcode must match binary peeking opcode")
        assertEquals(listOf(3, 4, 5), inMemTags)
        assertEquals(inMemTags, binTags, "In-memory changed tags must equal binary peeked tags")
    }

    @Test
    fun should_maintainSummaryParity_on_partialUpdate() = runEnv {
        val oldEntity = FeatureEntity()
        val newEntity = oldEntity.copy(countValue = 99)
        val bytes = encode(new = newEntity, old = oldEntity)
        val changedTags = computeChangedTags(newEntity, oldEntity)

        // When
        val inMemorySummary = summarize(MutationOp.UPSERT, changedTags)
        val binarySummary = reconstructSummary(bytes!!)

        val (inMemOp, inMemTags) = parseSummary(inMemorySummary)
        val (binOp, binTags) = parseSummary(binarySummary)

        assertEquals("UPSERT", inMemOp)
        assertEquals(inMemOp, binOp)
        assertEquals(listOf(5), inMemTags, "Only countValue (Tag 5) should be present")
        assertEquals(inMemTags, binTags)
    }

    @Test
    fun should_maintainSummaryParity_on_tombstoneDelete() = runEnv {
        val oldEntity = FeatureEntity()
        val deletedEntity = oldEntity.withDeleteState(true)
        val bytes = encode(new = deletedEntity, old = oldEntity)
        val changedTags = computeChangedTags(deletedEntity, oldEntity)

        // When
        val inMemorySummary = summarize(MutationOp.DELETE, changedTags)
        val binarySummary = reconstructSummary(bytes!!)

        val (inMemOp, inMemTags) = parseSummary(inMemorySummary)
        val (binOp, binTags) = parseSummary(binarySummary)

        assertEquals("DELETE", inMemOp)
        assertEquals(inMemOp, binOp, "Both summary paths must resolve tombstone DELETE")
        assertEquals(1, inMemTags.size)
        assertEquals(1, binTags.size)
    }

    // -------------------------------------------------------------------
    // BINARY CORRUPTION & BUFFER PEEKING
    // -------------------------------------------------------------------

    @Test
    fun should_returnInvalidEmptyBytes_when_payloadIsEmpty() = runEnv {
        val emptyBytes = ByteArray(0)

        val summary = reconstructSummary(emptyBytes)

        assertEquals(
            "OP:INVALID_EMPTY_BYTES",
            summary,
            "Empty ByteArray must short-circuit upfront"
        )
    }

    @Test
    fun should_returnCorruptPacket_when_varintIsTruncated() = runEnv {
        // Construct a varint with MSB 0x80 set, indicating more bytes follow, but terminate the array abruptly
        val truncatedVarintBytes = byteArrayOf(0x80.toByte())

        val summary = reconstructSummary(truncatedVarintBytes)

        assertEquals(
            "OP:CORRUPT_PACKET",
            summary,
            "Truncated MSB varint must trigger OP:CORRUPT_PACKET"
        )
    }

    @Test
    fun should_returnCorruptPacket_when_wireTypeIsInvalid() = runEnv {
        // Construct illegal wire key: Tag = 1, WireType = 6 -> (1 shl 3) or 6 = 14 (0x0E)
        val invalidWireTypeBytes = byteArrayOf(0x0E.toByte(), 0x01.toByte())

        val summary = reconstructSummary(invalidWireTypeBytes)

        assertEquals(
            "OP:CORRUPT_PACKET",
            summary,
            "Unsupported wire type (6) must trigger OP:CORRUPT_PACKET"
        )
    }

    @Test
    fun should_skipLengthDelimitedPayloads_without_corruptingBuffer() = runEnv {
        // Construct a large string payload (Wire Type 2) to force multi-byte length skipping
        val largeText = "A".repeat(2048)
        val entityWithLargePayload = FeatureEntity(
            triStateValue = TriState.TRUE,     // Tag 2
            textValue = largeText,             // Tag 3 (Wire Type 2, length 2048)
            countValue = 999                   // Tag 4 (Wire Type 0)
        )

        val bytes = encode(new = entityWithLargePayload, old = null)!!
        assertTrue(bytes.size > 2048, "Payload must be larger than 2KB")

        val summary = reconstructSummary(bytes)

        // Verify peeking engine correctly skipped 2048 bytes of string payload and aligned to countValue tag
        assertTrue(
            summary.startsWith("OP:UPSERT"),
            "Must successfully parse opcode despite skipping large length-delimited payload"
        )
        assertTrue(
            summary.contains("3") && summary.contains("4") && summary.contains("5"),
            "Summary vector must contain tag 3 (triStateValue), tag 4 (textValue), and tag 5 (countValue)"
        )
    }

    @Test
    fun should_cleanlyResetBuffer_across_sequentialPeeks() = runEnv {
        // Given Payload A: Large payload containing Tags 3, 4, and 5
        val largeEntity = FeatureEntity(
            triStateValue = TriState.TRUE, // Tag 3
            textValue = "Long text string to pad the buffer size", // Tag 4
            countValue = 99999 // Tag 5
        )
        val largeBytes = encode(new = largeEntity, old = null)
        assertNotNull(largeBytes)
        // Given Payload B: Small payload containing only Tag 3
        val smallEntity = largeEntity.copy(
            triStateValue = TriState.FALSE // Tag 3
        )
        val smallBytes = encode(new = smallEntity, old = largeEntity)
        assertNotNull(smallBytes)

        // When 1: Peek large payload into singleSharedBuffer
        val summary1 = reconstructSummary(largeBytes)
        assertEquals("OP:UPSERT [3,4,5]", summary1)
        // When 2: Peek small payload into the EXACT SAME singleSharedBuffer instance
        val summary2 = reconstructSummary(smallBytes)

        // Assert buffer behavior.
        assertEquals(
            "OP:UPSERT [3]",
            summary2,
            "Sequential peeking over a reused Buffer instance must cleanly wipe trailing bytes from prior executions"
        )
    }


    // --- HELPER ---
    private fun parseSummary(summary: String): Pair<String, List<Int>> {
        val parts = summary.split(" ")
        val rawOp = parts[0].removePrefix("OP:")

        val tags = if (parts.size > 1) {
            parts[1]
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .filter { it.isNotBlank() }
                .map { it.toInt() }
                .filter { it != 1 }
        } else {
            emptyList()
        }

        return rawOp to tags
    }
}

