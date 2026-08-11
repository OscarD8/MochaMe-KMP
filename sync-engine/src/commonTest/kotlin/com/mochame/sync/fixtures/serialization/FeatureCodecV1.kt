package com.mochame.sync.fixtures.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.common.TriState
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodec
import com.mochame.sync.spi.infrastructure.serialization.FieldMergeScope
import com.mochame.sync.spi.infrastructure.serialization.diff
import com.mochame.sync.spi.models.DecodeContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@Serializable
@ExperimentalSerializationApi
internal data class FeatureEntityDeltaV1(
    @ProtoNumber(1) val id: Long,
    @ProtoNumber(2) val isDeleted: Boolean? = null,
    @ProtoNumber(3) val triStateValue: TriState? = null,
    @ProtoNumber(4) val textValue: String? = null,
    @ProtoNumber(5) val countValue: Int? = null
)

@Single
@OptIn(ExperimentalSerializationApi::class)
internal class FeatureCodecV1(
    bufferProvider: BufferProvider,
    logger: Logger
) : BaseFeatureCodec<FeatureEntity, FeatureEntityDeltaV1>(
    bufferProvider = bufferProvider,
    deltaSerializer = FeatureEntityDeltaV1.serializer(),
    logger = logger.withTags(LogTags.Layer.SERI, LogTags.Domain.SYNC, "TeCdc1")
) {

    override fun buildDeleteDelta(entity: FeatureEntity) = FeatureEntityDeltaV1(
        id = entity.id,
        isDeleted = true
    )

    override fun buildInsertDelta(entity: FeatureEntity) = FeatureEntityDeltaV1(
        id = entity.id,
        triStateValue = entity.triStateValue,
        textValue = entity.textValue,
        countValue = entity.countValue
    )

    override fun buildUpdateDelta(new: FeatureEntity, old: FeatureEntity): FeatureEntityDeltaV1? {
        val triStateValue = new.triStateValue diff old.triStateValue
        val textDelta = new.textValue diff old.textValue
        val countDelta = new.countValue diff old.countValue

        if (textDelta == null && countDelta == null && triStateValue == null) return null

        return FeatureEntityDeltaV1(
            id = new.id,
            triStateValue = triStateValue,
            textValue = textDelta,
            countValue = countDelta,
        )
    }

    override fun FieldMergeScope.mergeDelta(
        delta: FeatureEntityDeltaV1,
        context: DecodeContext,
        existing: FeatureEntity?
    ): FeatureEntity = FeatureEntity(
        id = context.candidateKey,
        hlc = context.hlc,
        lastModified = context.hlc.ts,

        triStateValue = eval(3, delta.triStateValue, existing?.triStateValue ?: TriState.UNSET),
        textValue = eval(4, delta.textValue, existing?.textValue ?: ""),
        countValue = eval(5, delta.countValue, existing?.countValue ?: 0)
    )

    override fun computeDomainChangedTags(new: FeatureEntity, old: FeatureEntity?) = buildList {
        if (old == null || new.triStateValue != old.triStateValue) add(3)
        if (old == null || new.textValue != old.textValue) add(4)
        if (old == null || new.countValue != old.countValue) add(5)
    }

}