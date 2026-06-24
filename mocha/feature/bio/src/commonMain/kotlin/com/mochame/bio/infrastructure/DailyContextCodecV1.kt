package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.contract.providers.BufferProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.models.DecodeContext
import com.mochame.sync.contract.serialization.FeatureCodec
import com.mochame.utils.readProtobufVarint
import com.mochame.utils.skipProtobufValue
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@ExperimentalSerializationApi
@Serializable
internal data class DailyContextDeltaV1(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val sleepHours: Double? = null,
    @ProtoNumber(3) val readinessScore: Int? = null,
    @ProtoNumber(4) val isNapped: Boolean? = null,
    @ProtoNumber(5) val isDeleted: Boolean = false
)

/**
 * V1 of the DailyContext codec.
 */
@Single
class DailyContextCodecV1(
    override val bufferProvider: BufferProvider,
    logger: Logger
) : FeatureCodec<DailyContext> {

    private val logger =
        logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.BIO, "BioCodecV1")

    /**
     * Returns null if no fields have changed, aborting write.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun encode(new: DailyContext, old: DailyContext?): ByteArray? {
        return when {
            new.isDeleted -> encodeDelta(
                DailyContextDeltaV1(
                    id = new.id,
                    isDeleted = true
                )
            )

            old == null -> encodeDelta(
                DailyContextDeltaV1(
                    id = new.id,
                    sleepHours = new.sleepHours,
                    readinessScore = new.readinessScore,
                    isNapped = new.isNapped
                )
            )

            else -> {
                val sleep = if (new.sleepHours != old.sleepHours) new.sleepHours else null
                val readiness =
                    if (new.readinessScore != old.readinessScore) new.readinessScore else null
                val napped = if (new.isNapped != old.isNapped) new.isNapped else null

                if (sleep == null && readiness == null && napped == null) return null

                encodeDelta(
                    DailyContextDeltaV1(
                        id = new.id,
                        sleepHours = sleep,
                        readinessScore = readiness,
                        isNapped = napped
                    )
                )
            }
        }
    }

    /**
     * Peek (Objects no longer in memory).
     * Extracts tags from raw bits without full value decoding.
     * Uses source.peek() to ensure it is completely non-destructive.
     */
    override fun reconstructSummary(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            logger.w { "Summary Failed: Protocol Version Mismatch. Got ${bytes.getOrNull(0)}" }
            return "OP:INVALID_VERSION"
        }

        val buffer = bufferProvider.get().apply {
            this.clear()
            this.write(bytes)
        }

        return try {
            val peekSource = buffer.peek()
            peekSource.readByte() // Skip version header

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

    /**
     * Reconstructs a DailyContext from passed bytes.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun decode(
        bytes: ByteArray,
        context: DecodeContext
    ): DailyContext {
        val delta = ProtoBuf.decodeFromByteArray(DailyContextDeltaV1.serializer(), bytes)

        return DailyContext(
            id = context.id,
            hlc = context.hlc,
            lastModified = context.lastModified,
            epochDay = context.id.toLong(),
            sleepHours = delta.sleepHours ?: 0.0,
            readinessScore = delta.readinessScore ?: 0,
            isNapped = delta.isNapped ?: false,
            isDeleted = delta.isDeleted
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun encodeDelta(delta: DailyContextDeltaV1): ByteArray {
        return ProtoBuf.encodeToByteArray(DailyContextDeltaV1.serializer(),delta)
    }

}