package com.mochame.sync.fixtures.serialization

import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodec
import com.mochame.sync.spi.models.DecodeContext
import org.koin.core.annotation.Single

@Single
class TestEntityCodecV2(
    override val bufferProvider: BufferProvider
) : FeatureCodec<TestEntity> {
    companion object {
        val V2_DISTINCT_BYTES = byteArrayOf(0x02, 0x02)
        val V2_DECODED_MARKER = TestEntity(
            id = "V2_MARKER_ENTITY",
            hlc = HLC.parse("0:0:v2node"),
            lastModified = 9999L,
            textValue = "DECODED_VIA_V2_FAKE"
        )
    }

    override fun encode(new: TestEntity, old: TestEntity?): ByteArray = V2_DISTINCT_BYTES

    override fun decode(
        bytes: ByteArray,
        context: DecodeContext,
        existing: TestEntity?
    ): TestEntity {
        if (bytes.contentEquals(V2_DISTINCT_BYTES)) {
            return V2_DECODED_MARKER.copy(
                id = context.candidateKey,
                hlc = context.hlc,
                lastModified = context.hlc.ts
            )
        }
        error("FakeTestEntityCodecV2 received unexpected payload bytes: ${bytes.toHexString()}")
    }

    override fun summarize(new: TestEntity, old: TestEntity?): String = "OP:V2_SUMMARY"
    override fun reconstructSummary(bytes: ByteArray): String = "OP:V2_RECONSTRUCTED"
}