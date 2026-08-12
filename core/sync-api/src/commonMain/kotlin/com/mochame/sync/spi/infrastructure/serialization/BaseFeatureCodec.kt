package com.mochame.sync.spi.infrastructure.serialization

import co.touchlab.kermit.Logger
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.models.LocalFirstDelta
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.readProtobufVarint
import com.mochame.sync.common.skipProtobufValue
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.models.DecodeContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Centralizes standard domain delta logic.
 * All implementing [FeatureCodec]s and their Serializable deltas must define:
 * * Primary Key (id) : [TAG_PRIMARY_KEY]
 * * isDeleted: [TAG_IS_DELETED]
 * * Domain Fields: [FIRST_DOMAIN_TAG]+
 * ```kotlin
 * internal data class FeatureEntityDeltaV1(
 *     @ProtoNumber(1) val id: Long,
 *     @ProtoNumber(2) val isDeleted: Boolean? = null,
 * ```
 *
 * * [T] = Main Domain Entity (e.g. DailyContext)
 * * [D] = Serializable Protobuf Delta Schema (e.g. DailyContextDeltaV1)
 */
@ExperimentalSerializationApi
abstract class BaseFeatureCodec<T : LocalFirstEntity<T>, D : LocalFirstDelta>(
    override val bufferProvider: BufferProvider,
    private val deltaSerializer: KSerializer<D>,
    protected val logger: Logger
) : FeatureCodec<T> {

    init {
        val descriptor = deltaSerializer.descriptor
        require(descriptor.elementsCount >= 2) { "Schema Error: ${descriptor.serialName} needs at least 2 properties." }
        require(descriptor.getElementName(0) == "id") { "Schema Error: Tag 1 in ${descriptor.serialName} must be 'id'." }
        require(descriptor.getElementName(1) == "isDeleted") { "Schema Error: Tag 2 in ${descriptor.serialName} must be 'isDeleted'." }
    }

    companion object {
        const val TAG_PRIMARY_KEY = 1
        const val TAG_IS_DELETED = 2
        const val FIRST_DOMAIN_TAG = 3
    }

    // -----------------------------------------------------------------
    // ENCODE: T -> D -> Bytes
    // -----------------------------------------------------------------
    override fun encode(new: T, old: T?): ByteArray? {
        val delta = when {
            new.isDeleted -> buildDeleteDelta(new)
            old == null -> buildInsertDelta(new)
            else -> { buildUpdateDelta(new, old, isResurrection = old.isDeleted) }
        } ?: return null

        return try {
            val bytes = ProtoBuf.encodeToByteArray(deltaSerializer, delta)
            logger.v { "Encoded ${deltaSerializer.descriptor.serialName.substringAfterLast(".")} [${bytes.size}B] key=${new.id}" }
            bytes
        } catch (e: Exception) {
            logger.e(e) { "Failed to encode delta payload for entity key=${new.id}" }
            throw e
        }
    }

    // -----------------------------------------------------------------
    // DECODE: Bytes -> D -> T
    // -----------------------------------------------------------------
    override fun decode(
        bytes: ByteArray,
        context: DecodeContext,
        existing: T?
    ): T {
        logger.v { "Decoding delta [${bytes.size}B] key=${context.candidateKey} hlc=${context.hlc}" }

        val delta = try {
            ProtoBuf.decodeFromByteArray(deltaSerializer, bytes)
        } catch (e: Exception) {
            logger.e(e) { "Protobuf decoding failed: key=${context.candidateKey} hlc=${context.hlc} schema=${context.featureSchemaVersion} (${bytes.size} bytes)" }
            throw e
        }

        val scope = FieldMergeScope(existing?.fieldHlcs ?: ByteArray(0), context.hlc)
        val mergedEntity = scope.mergeDelta(delta, context, existing)
        val deleteState = scope.resolveDeleteState(delta.isDeleted, existing?.isDeleted)

        return mergedEntity
            .withFieldHlcs(scope.buildResultBlob())
            .withDeleteState(deleteState)
            .also { logger.d { "Decoding finalized. key=${it.id}" } }
    }

    private fun FieldMergeScope.resolveDeleteState(
        deltaIsDeleted: Boolean?,
        existingIsDeleted: Boolean?,
    ): Boolean {
        val localTombstoneHlc = getTagHlc(TAG_IS_DELETED)
        val isNewer = localTombstoneHlc == null || incomingHlc > localTombstoneHlc

        return when {
            deltaIsDeleted == true -> {
                if (isNewer) {
                    updateTag(TAG_IS_DELETED, incomingHlc)
                    true
                } else {
                    existingIsDeleted ?: true
                }
            }

            existingIsDeleted == true -> {
                if (isNewer) {
                    updateTag(TAG_IS_DELETED, incomingHlc)
                    false
                } else {
                    true
                }
            }

            else -> false
        }
    }

    /**
     * Peek (Objects no longer in memory).
     * Extracts tags from raw bits without full value decoding.
     * Uses source.peek().
     */
    override fun reconstructSummary(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            logger.w { "Summary Reconstruction Failed. Received empty ByteArray." }
            return "OP:INVALID_EMPTY_BYTES"
        }

        val buffer = bufferProvider.get().apply {
            this.clear()
            this.write(bytes)
        }

        return try {
            val peekSource = buffer.peek()

            var isTombstone = false
            val tags = buildList {
                while (!peekSource.exhausted()) {
                    val key = peekSource.readProtobufVarint(logger)
                    val tag = key shr 3
                    if (tag == TAG_IS_DELETED) add(tag).also { isTombstone = true }
                    if (tag >= FIRST_DOMAIN_TAG) add(tag)
                    peekSource.skipProtobufValue(key and 0x07, logger)
                }
            }

            val opCode = if (isTombstone) "DELETE" else "UPSERT"
            with("OP:$opCode [${tags.distinct().sorted().joinToString(",")}]") {
                logger.d { "Reconstructed Summary: $this" }
                return this
            }
        } catch (e: Exception) {
            logger.e(e) { "Packet summary reconstruction failed on payload size=${bytes.size}B" }
            "OP:CORRUPT_PACKET"
        }
    }

    override fun summarize(op: MutationOp, changedTags: List<Int>): String {
        val op = if (op == MutationOp.DELETE) "DELETE" else "UPSERT"

        with(
            "OP:${op} ${changedTags.joinToString(prefix = "[", postfix = "]", separator = ",")}"
        ) {
            logger.d { "In-Memory Summary: $this" }
            return this
        }
    }

    /**
     * Automatically computes Tags 1 & 2 (id, isDeleted)
     * and delegates domain field diffing (Tag 3+) to [computeDomainChangedTags].
     */
    override fun computeChangedTags(new: T, old: T?): List<Int> = buildList {
        val isTombstoneStateChanged = new.isDeleted != (old?.isDeleted ?: false)

        if (isTombstoneStateChanged) add(TAG_IS_DELETED)

        if (!new.isDeleted) {
            if (old == null) add(TAG_PRIMARY_KEY)
            addAll(computeDomainChangedTags(new, old))
        }
    }

    // --- FEATURE REQUIREMENTS ---
    protected abstract fun buildDeleteDelta(entity: T): D
    protected abstract fun buildInsertDelta(entity: T): D
    protected abstract fun buildUpdateDelta(new: T, old: T, isResurrection: Boolean): D?

    /**
     * Compute changed tag IDs strictly for domain fields (excluding Tags 1 [LocalFirstDelta.id] & 2 [LocalFirstDelta.isDeleted]).
     * Features only work from [FIRST_DOMAIN_TAG]+
     */
    protected abstract fun computeDomainChangedTags(new: T, old: T?): List<Int>

    /**
     * Maps the decoded protobuf delta [D] onto domain entity [T] using [DecodeContext] and optional [existing] state.
     */
    protected abstract fun FieldMergeScope.mergeDelta(
        delta: D,
        context: DecodeContext,
        existing: T?
    ): T

}

@Suppress("NOTHING_TO_INLINE")
inline infix fun <T> T.diff(old: T): T? = if (this != old) this else null