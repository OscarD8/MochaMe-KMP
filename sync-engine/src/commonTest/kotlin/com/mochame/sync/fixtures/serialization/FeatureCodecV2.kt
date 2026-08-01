package com.mochame.sync.fixtures.serialization

import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodec
import com.mochame.sync.spi.models.DecodeContext
import org.koin.core.annotation.Single

@Single
class FeatureCodecV2(
    override val bufferProvider: BufferProvider
) : FeatureCodec<FeatureEntity> {
    companion object {
        val BYTES_PRESET = byteArrayOf(0x02, 0x02)
        val MODEL_PRESET = FeatureEntity(
            id = "V2_MARKER_ENTITY",
            textValue = "DECODED_VIA_V2_FAKE"
        )

        const val SUMMARIZE_PRESET = "OP:V2_SUMMARY"
        const val RECONSTRUCT_PRESET = "OP:V2_RECONSTRUCTED"
    }

    override fun encode(new: FeatureEntity, old: FeatureEntity?): ByteArray = BYTES_PRESET

    override fun decode(
        bytes: ByteArray,
        context: DecodeContext,
        existing: FeatureEntity?
    ): FeatureEntity {
        if (bytes.contentEquals(BYTES_PRESET)) {
            return MODEL_PRESET.copy(
                id = context.candidateKey,
                hlc = context.hlc,
                lastModified = context.hlc.ts
            )
        }
        error("FakeTestEntityCodecV2 received unexpected payload bytes: ${bytes.toHexString()}")
    }

    override fun summarize(new: FeatureEntity, old: FeatureEntity?): String = SUMMARIZE_PRESET
    override fun reconstructSummary(bytes: ByteArray): String = RECONSTRUCT_PRESET
}