package com.mochame.bio

import co.touchlab.kermit.Logger
import com.mochame.app.domain.feature.bio.DailyContext
import com.mochame.app.domain.sync.model.EntityMetadata
import com.mochame.app.infrastructure.sync.BasePayloadEncoder
import com.mochame.app.infrastructure.utils.BufferProvider
import kotlinx.io.Buffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

@ExperimentalSerializationApi
@Serializable
internal data class BioDeltaV1(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val sleepHours: Double? = null,
    @ProtoNumber(3) val readinessScore: Int? = null,
    @ProtoNumber(4) val isNapped: Boolean? = null,
    @ProtoNumber(5) val isDeleted: Boolean = false
)


class BioPayloadEncoderV1(logger: Logger, bufferProvider: BufferProvider) :
    BasePayloadEncoder<DailyContext>(
        version = 0x01,
        logger = logger.withTag("BioEncoder"),
        bufferProvider = bufferProvider
    ) {

    /**
     * Returns null if no fields have changed, aborting write.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun generateDelta(new: DailyContext, old: DailyContext?): ByteArray? {
        return when {
            new.isDeleted -> encodeDelta(BioDeltaV1(id = new.id, isDeleted = true))

            old == null -> encodeDelta(
                BioDeltaV1(
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
                    BioDeltaV1(
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
     */
    override fun reconstructSummary(data: ByteArray): String {
        if (!validate(data)) {
            logger.w { "Summary Failed: Protocol Version Mismatch. Got ${data.getOrNull(0)}" }
            return "OP:INVALID_VERSION"
        }

        val buffer = bufferProvider.get().apply {
            Buffer.clear()
            Buffer.write(data)
        }

        return try {
            val peekSource = buffer.peek() // Non-destructive Zero-Copy Scan
            peekSource.readByte() // Skip version header

            var isTombstone = false
            val tags = buildList {
                while (!peekSource.exhausted()) {
                    val key = readVarint(peekSource)
                    val tag = key shr 3
                    if (tag == 5) isTombstone = true
                    if (tag in 1..5) add(tag)
                    skipValue(key and 0x07, peekSource)
                }
            }

            val opCode = if (isTombstone) "DELETE" else "UPSERT"
            "OP:${opCode}_V1 [${tags.distinct().sorted().joinToString(",")}]"
        } catch (e: Exception) {
            logger.e(e) { "Forensics: Packet reconstruction failed (${data.size} bytes)" }
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

    override fun validate(data: ByteArray): Boolean = data.size > 1 && data[0] == version

    /**
     * Reanimates a DailyContext from V1 Protobuf bits.
     */
    @OptIn(ExperimentalSerializationApi::class)
    override fun internalDecode(
        payloadBits: ByteArray,
        metadata: EntityMetadata
    ): DailyContext {
        // 1. Deserialize the Delta
        val delta = ProtoBuf.decodeFromByteArray(BioDeltaV1.serializer(), payloadBits)

        // 2. Reconstruct the full state using the "Envelope" (The SQL fields)
        return DailyContext(
            id = metadata.id,
            hlc = metadata.hlc,
            lastModified = metadata.lastModified,
            epochDay = metadata.id.toLong(),
            sleepHours = delta.sleepHours ?: 0.0,
            readinessScore = delta.readinessScore ?: 0,
            isNapped = delta.isNapped ?: false,
            isDeleted = delta.isDeleted
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun encodeDelta(delta: BioDeltaV1): ByteArray {
        return ProtoBuf.encodeToByteArray(BioDeltaV1.serializer(), delta)
    }
}