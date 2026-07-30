package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.common.TriState
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodec
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.utils.readProtobufVarint
import com.mochame.utils.skipProtobufValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Serializable
data class DailyContextDeltaV1(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val sleepHours: Double? = null,
    @ProtoNumber(3) val readinessScore: Int? = null,
    @ProtoNumber(4) val isNapped: TriState? = null,
    @ProtoNumber(5) val isDeleted: Boolean? = null
)

/**
 * V1 of the DailyContext codec.
 */
@OptIn(ExperimentalSerializationApi::class)
@Single
internal class DailyContextCodecV1(
    bufferProvider: BufferProvider,
    logger: Logger
) : BaseFeatureCodec<DailyContext, DailyContextDeltaV1>(
    bufferProvider = bufferProvider,
    deltaSerializer = DailyContextDeltaV1.serializer(),
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.BIO, "DyCdc1")
) {

    override fun buildDeleteDelta(entity: DailyContext) = DailyContextDeltaV1(
        id = entity.id,
        isDeleted = true
    )

    override fun buildInsertDelta(entity: DailyContext) = DailyContextDeltaV1(
        id = entity.id,
        sleepHours = entity.sleepHours,
        readinessScore = entity.readinessScore,
        isNapped = entity.isNapped
    )

    override fun buildUpdateDelta(
        new: DailyContext,
        old: DailyContext
    ): DailyContextDeltaV1? {
        val sleep = if (new.sleepHours != old.sleepHours) new.sleepHours else null
        val readiness =
            if (new.readinessScore != old.readinessScore) new.readinessScore else null
        val napped = if (new.isNapped != old.isNapped) new.isNapped else null

        if (sleep == null && readiness == null && napped == null) return null

        return DailyContextDeltaV1(
            id = new.id,
            sleepHours = sleep,
            readinessScore = readiness,
            isNapped = napped
        )
    }

    /**
     * Reconstructs a DailyContext from passed bytes.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun decode(
        bytes: ByteArray,
        context: DecodeContext,
        existing: DailyContext?
    ): DailyContext {
        val delta = ProtoBuf.decodeFromByteArray(DailyContextDeltaV1.serializer(), bytes)

        return DailyContext(
            id = context.candidateKey,
            hlc = context.hlc,
            lastModified = context.lastModified,
            epochDay = context.candidateKey.toLong(),

            sleepHours = delta.sleepHours ?: existing?.sleepHours ?: 0.0,
            readinessScore = delta.readinessScore ?: existing?.readinessScore ?: 0,
            isNapped = delta.isNapped ?: existing?.isNapped ?: TriState.UNSET,
            isDeleted = delta.isDeleted ?: existing?.isDeleted ?: false
        )
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
                    if (tag == 5) isTombstone = true
                    if (tag in 1..5) add(tag)
                    peekSource.skipProtobufValue(key and 0x07, logger)
                }
            }

            val opCode = if (isTombstone) "DELETE" else "UPSERT"
            "OP:${opCode}_V1 [${tags.distinct().sorted().joinToString(",")}]"
        } catch (e: Exception) {
            logger.e(e) { "Packet reconstruction failed (${bytes.size} bytes)" }
            "OP:CORRUPT_PACKET"
        }
    }

    /**
     * Mutation-Time Summary (The actual objects are in memory).
     */
    override fun summarize(new: DailyContext, old: DailyContext?): String {
        if (new.isDeleted) return "OP:DELETE"

        val tags = buildList {
            if (old == null || new.sleepHours != old.sleepHours) add(2)
            if (old == null || new.readinessScore != old.readinessScore) add(3)
            if (old == null || new.isNapped != old.isNapped) add(4)
        }

        return "OP:UPSERT_V1 ${
            tags.joinToString(prefix = "[", postfix = "]", separator = ",")
        }"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun encodeDelta(delta: DailyContextDeltaV1): ByteArray {
        return ProtoBuf.encodeToByteArray(DailyContextDeltaV1.serializer(), delta)
    }

}