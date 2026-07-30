package com.mochame.sync.fixtures.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.common.TriState
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodec
import com.mochame.sync.spi.models.DecodeContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.core.annotation.Single

@Serializable
@ExperimentalSerializationApi
internal data class TestEntityDeltaV1(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val triStateValue: TriState? = null,
    @ProtoNumber(3) val textValue: String? = null,
    @ProtoNumber(4) val countValue: Int? = null,
    @ProtoNumber(5) val isDeleted: Boolean? = null
)

@Single
@OptIn(ExperimentalSerializationApi::class)
internal class TestEntityCodecV1(
    bufferProvider: BufferProvider,
    logger: Logger
) : BaseFeatureCodec<TestEntity, TestEntityDeltaV1>(
    bufferProvider = bufferProvider,
    deltaSerializer = TestEntityDeltaV1.serializer(),
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.SYNC, "TeCdc1")
) {

    override fun buildDeleteDelta(entity: TestEntity) = TestEntityDeltaV1(
        id = entity.id,
        isDeleted = true
    )

    override fun buildInsertDelta(entity: TestEntity) = TestEntityDeltaV1(
        id = entity.id,
        textValue = entity.textValue,
        countValue = entity.countValue
    )

    override fun buildUpdateDelta(new: TestEntity, old: TestEntity): TestEntityDeltaV1? {
        val textDelta = if (new.textValue != old.textValue) new.textValue else null
        val countDelta = if (new.countValue != old.countValue) new.countValue else null

        if (textDelta == null && countDelta == null) return null

        return TestEntityDeltaV1(
            id = new.id,
            textValue = textDelta,
            countValue = countDelta,
        )
    }

    override fun decode(
        bytes: ByteArray,
        context: DecodeContext,
        existing: TestEntity?
    ): TestEntity {
        val delta = ProtoBuf.decodeFromByteArray(TestEntityDeltaV1.serializer(), bytes)
        return TestEntity(
            id = context.candidateKey,
            hlc = context.hlc,
            lastModified = context.lastModified,

            triStateValue = delta.triStateValue ?: existing?.triStateValue ?: TriState.UNSET,
            textValue = delta.textValue ?: existing?.textValue ?: "",
            countValue = delta.countValue ?: existing?.countValue ?: 0,
            isDeleted = delta.isDeleted ?: existing?.isDeleted ?: false
        )
    }

    override fun summarize(new: TestEntity, old: TestEntity?): String =
        if (new.isDeleted) "OP:DELETE" else "OP:UPSERT_V1"

    override fun reconstructSummary(bytes: ByteArray): String = "OP:RECONSTRUCTED_V1"
}