package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.models.LocalFirstDelta
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

@ExperimentalSerializationApi
@Serializable
data class DailyContextDeltaV1(
    @ProtoNumber(1) override val id: Long,
    @ProtoNumber(2) override val isDeleted: Boolean? = null,
    @ProtoNumber(3) val sleepHours: Double? = null,
    @ProtoNumber(4) val readinessScore: Int? = null,
    @ProtoNumber(5) val isNapped: TriState? = null,
) : LocalFirstDelta


@OptIn(ExperimentalSerializationApi::class)
@Single
internal class DailyContextCodecV1(
    bufferProvider: BufferProvider,
    logger: Logger
) : BaseFeatureCodec<DailyContext, DailyContextDeltaV1>(
    bufferProvider = bufferProvider,
    deltaSerializer = DailyContextDeltaV1.serializer(),
    logger = logger.withTags(LogTags.Layer.SERI, LogTags.Domain.BIO, "DyCdc1")
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
        old: DailyContext,
        isResurrection: Boolean
    ): DailyContextDeltaV1? {
        val sleep = new.sleepHours diff old.sleepHours
        val readiness = new.readinessScore diff old.readinessScore
        val napped = new.isNapped diff old.isNapped

        if (sleep == null && readiness == null && napped == null && !isResurrection) return null

        return DailyContextDeltaV1(
            id = new.id,
            isDeleted = false.takeIf { isResurrection },
            sleepHours = sleep,
            readinessScore = readiness,
            isNapped = napped
        )
    }

    override fun FieldMergeScope.mergeDelta(
        delta: DailyContextDeltaV1,
        context: DecodeContext,
        existing: DailyContext?
    ) = DailyContext(
        id = context.candidateKey,
        hlc = context.hlc,
        lastModified = context.hlc.ts,

        sleepHours = eval(3, delta.sleepHours, existing?.sleepHours ?: 0.0),
        readinessScore = eval(4, delta.readinessScore, existing?.readinessScore ?: 0),
        isNapped = eval(5, delta.isNapped, existing?.isNapped ?: TriState.UNSET),
    )

    override fun computeDomainChangedTags(new: DailyContext, old: DailyContext?): List<Int> =
        buildList {
            if (old == null || new.sleepHours != old.sleepHours) add(3)
            if (old == null || new.readinessScore != old.readinessScore) add(4)
            if (old == null || new.isNapped != old.isNapped) add(5)
        }

}