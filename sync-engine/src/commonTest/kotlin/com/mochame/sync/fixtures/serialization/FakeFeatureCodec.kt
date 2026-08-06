package com.mochame.sync.fixtures.serialization

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodec
import com.mochame.sync.spi.models.DecodeContext
import org.koin.core.annotation.Single

@Single
internal class FakeFeatureCodec(
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
        require(bytes.contentEquals(BYTES_PRESET)) {
            "FakeFeatureCodec decode received unexpected bytes: ${bytes.toHexString()}"
        }

        return MODEL_PRESET.copy(
            id = context.candidateKey,
            hlc = context.hlc,
            lastModified = context.hlc.ts
        )
    }

    override fun summarize(op: MutationOp, changedTags: List<Int>): String = SUMMARIZE_PRESET
    override fun reconstructSummary(bytes: ByteArray): String = RECONSTRUCT_PRESET
    override fun computeChangedTags(new: FeatureEntity, old: FeatureEntity?) = emptyList<Int>()
}