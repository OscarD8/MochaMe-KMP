package com.mochame.sync.fixtures.serialization

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.models.LocalFirstEntity
import com.mochame.sync.common.TriState
import com.mochame.sync.spi.models.DecodeContext
import com.mochame.utils.fixtures.HlcTestFactory

data class FeatureEntity(
    override val id: String = "default-entity-1",
    override val hlc: HLC = HlcTestFactory.create(),
    override val lastModified: Long = HlcTestFactory.create().ts,
    override val isDeleted: Boolean = false,
    val triStateValue: TriState = TriState.TRUE,
    val textValue: String = "default-text",
    val countValue: Int = 1,
) : LocalFirstEntity<FeatureEntity> {
    override fun withHlc(hlc: HLC): FeatureEntity = copy(hlc = hlc)
    override fun markDeleted(): FeatureEntity = copy(isDeleted = true)
}

fun FeatureEntity.deriveContext(
    schemaVersion: Int = 1,
    op: MutationOp = MutationOp.UPSERT,
    overflowBlobId: String? = null
) = DecodeContext(
    featureSchemaVersion = schemaVersion,
    candidateKey = id,
    hlc = hlc,
    op = op,
    overflowBlobId = overflowBlobId
)