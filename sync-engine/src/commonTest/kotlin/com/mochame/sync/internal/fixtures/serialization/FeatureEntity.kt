package com.mochame.sync.internal.fixtures.serialization

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.TestLogWriter
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.utils.fixtures.TestHlcFactory
import kotlin.test.assertEquals

data class FeatureEntity(
    override val id: Long = 1000L,
    override val hlc: HLC = TestHlcFactory.create(),
    override val lastModified: Long = TestHlcFactory.create().ts,
    override val isDeleted: Boolean = false,
    override val fieldHlcs: ByteArray = ByteArray(0),
    val textValue: String? = "default-text",
    val countValue: Int? = 1,
) : LocalFirstEntity<FeatureEntity> {
    override fun withHlc(hlc: HLC): FeatureEntity = copy(hlc = hlc)
    override fun withDeleteState(state: Boolean): FeatureEntity = copy(isDeleted = state)
    override fun withFieldHlcs(blob: ByteArray) = copy(fieldHlcs = blob)
}

fun FeatureEntity.deriveContext(
    schemaVersion: Int = 1,
    op: MutationOp = MutationOp.UPSERT,
    overflowBlobId: String? = null,
    changedMask: Long? = null
) = DecodeContext(
    featureSchemaVersion = schemaVersion,
    candidateKey = id,
    hlc = hlc,
    op = op,
    overflowBlobId = overflowBlobId,
    changedMask = changedMask ?: 0L
)

fun FeatureEntity.assertDecodeParity(original: FeatureEntity, upsertHlc: HLC? = null) {
    assertEquals(original.id, this.id)
    assertEquals(original.isDeleted, this.isDeleted)
    assertEquals(original.textValue, this.textValue)
    assertEquals(original.countValue, this.countValue)

    upsertHlc?.let{
        assertEquals(it, this.hlc)
        assertEquals(it.ts, this.lastModified)
    } ?: {
        assertEquals(original.hlc, this.hlc)
    }
}

@OptIn(ExperimentalKermitApi::class)
fun TestLogWriter.assertFieldRejectionLogCount(expectedCount: Int) =
    assertEquals(expectedCount, this.logs.count { it.message.contains("Field Rejected") })
