package com.mochame.sync.internal.fixtures.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.models.LocalFirstDelta
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
data class FeatureEntityDeltaV1(
    @ProtoNumber(1) override val id: Long,
    @ProtoNumber(2) override val isDeleted: Boolean? = null,
    @ProtoNumber(4) val textValue: String? = null,
    @ProtoNumber(5) val countValue: Int? = null
) : LocalFirstDelta

@Single
@OptIn(ExperimentalSerializationApi::class)
class FeatureCodecV1(
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
        textValue = entity.textValue,
        countValue = entity.countValue
    )

    override fun buildUpdateDelta(
        new: FeatureEntity,
        old: FeatureEntity,
        isRestored: Boolean
    ): FeatureEntityDeltaV1 = FeatureEntityDeltaV1(
        id = new.id,
        isDeleted = false.takeIf { isRestored },
        textValue = new.textValue diff old.textValue,
        countValue = new.countValue diff old.countValue
    )


    override fun FieldMergeScope.mergeDelta(
        delta: FeatureEntityDeltaV1,
        context: DecodeContext,
        existing: FeatureEntity?
    ): FeatureEntity = FeatureEntity(
        id = context.candidateKey,
        hlc = context.hlc,
        lastModified = context.hlc.ts,

        textValue = eval(4, delta.textValue, existing?.textValue),
        countValue = eval(5, delta.countValue, existing?.countValue)
    )

    override fun computeDomainChangedTags(new: FeatureEntity, old: FeatureEntity?) = buildList {
        if (old == null || new.textValue != old.textValue) add(4)
        if (old == null || new.countValue != old.countValue) add(5)
    }

}