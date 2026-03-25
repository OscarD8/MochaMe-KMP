package com.mochame.app.data.repository.telemetry.bridge

import com.benasher44.uuid.uuid4
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.data.mappers.toEntity
import com.mochame.app.data.local.room.dao.TelemetryDao
import com.mochame.app.domain.telemetry.Moment
import com.mochame.app.domain.telemetry.MomentClimate
import com.mochame.app.domain.telemetry.MomentDetail
import com.mochame.app.domain.telemetry.MomentDraft
import com.mochame.app.domain.telemetry.MomentMetadata
import com.mochame.app.domain.telemetry.repositories.MomentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ObservationBridge: SQLite-backed implementation of [MomentRepository].
 * * Uses [DateTimeUtils] to calculate biological anchors and
 * [TelemetryDao] for persistent storage.
 */
internal class MomentBridge(
    private val dao: TelemetryDao,
    private val dateTimeUtils: DateTimeUtils
) : MomentRepository {

    override suspend fun logMoment(draft: MomentDraft) = withContext(Dispatchers.IO) {
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
            metadata = createMetadata(now, biologicalDay) // Standardized assembly
        )

        dao.upsertMoment(newMoment.toEntity())
    }

    override suspend fun saveMoment(moment: Moment) = withContext(Dispatchers.IO) {
        val updatedMetaData = moment.metadata.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        val updatedMoment = moment.copy(
            metadata = updatedMetaData
        )

        dao.upsertMoment(updatedMoment.toEntity())
    }

    override suspend fun deleteMoment(momentId: String) = withContext(Dispatchers.IO) {
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