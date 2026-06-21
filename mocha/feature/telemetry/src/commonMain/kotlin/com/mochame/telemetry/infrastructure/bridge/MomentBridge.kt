package com.mochame.telemetry.infrastructure.bridge

import com.benasher44.uuid.uuid4
import com.mochame.contract.di.IoContext
import com.mochame.contract.providers.DateTimeProvider
import com.mochame.telemetry.data.TelemetryDao
import com.mochame.telemetry.data.toEntity
import com.mochame.telemetry.domain.Moment
import com.mochame.telemetry.domain.MomentClimate
import com.mochame.telemetry.domain.MomentDetail
import com.mochame.telemetry.domain.MomentDraft
import com.mochame.telemetry.domain.MomentMetadata
import com.mochame.telemetry.domain.repositories.MomentRepository
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * ObservationBridge: SQLite-backed implementation of [com.mochame.telemetry.domain.repositories.MomentRepository].
 * * Uses [com.mochame.sync.infrastructure.utils.DateTimeUtils] to calculate biological anchors and
 * [com.mochame.telemetry.data.TelemetryDao] for persistent storage.
 */
internal class MomentBridge(
    private val dao: TelemetryDao,
    private val dateTimeUtils: DateTimeProvider,
    @IoContext private val ioContext: CoroutineContext
) : MomentRepository {

    override suspend fun logMoment(draft: MomentDraft) = withContext(ioContext) {
        val now = dateTimeUtils.now().toEpochMilliseconds()

        val biologicalDay = dateTimeUtils.calculateMochaEpochDay(dateTimeUtils.now())
        val validatedDomainId = resolveDomainId(draft)
        val enrichedDetail = enrichBiophilia(draft.spaceId, draft.detail)

        val newMoment = Moment(
            id = uuid4().toString(),
            domainId = validatedDomainId,
            topicId = draft.topicId,
            spaceId = draft.spaceId,
            core = draft.core,
            detail = enrichedDetail,
            context = MomentClimate(),
            metadata = createMetadata(now, biologicalDay),
        )

        dao.upsertMoment(newMoment.toEntity())
    }

    override suspend fun saveMoment(moment: Moment) = withContext(ioContext) {
        val updatedMetaData = moment.metadata.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        val updatedMoment = moment.copy(
            metadata = updatedMetaData
        )

        dao.upsertMoment(updatedMoment.toEntity())
    }

    override suspend fun deleteMoment(momentId: String) = withContext(ioContext) {
        dao.deleteMomentById(momentId)
    }

    // --- Helpers ---
    private suspend fun resolveDomainId(draft: MomentDraft): String {
        return draft.topicId?.let { id ->
            dao.getTopicDomainId(id)
        } ?: draft.domainId
    }

    private suspend fun enrichBiophilia(
        spaceId: String?,
        currentDetail: MomentDetail
    ): MomentDetail {
        // 1If user already provided a scale, respect the manual override.
        if (currentDetail.biophiliaScale != null) return currentDetail

        // If no manual scale, check for a Space default.
        val defaultScale = spaceId?.let {
            dao.getSpaceById(it)?.defaultBiophilia
        }

        // Return a copy with the fallback or null.
        return currentDetail.copy(biophiliaScale = defaultScale)
    }

    private fun createMetadata(now: Long, day: Long) = MomentMetadata(
        timestamp = now,
        associatedEpochDay = day,
        lastModified = now
    )
}