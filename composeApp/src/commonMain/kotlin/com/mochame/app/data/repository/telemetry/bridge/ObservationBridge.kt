package com.mochame.app.data.repository.telemetry.bridge

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.model.MomentClimate
import com.mochame.app.domain.model.MomentDraft
import com.mochame.app.domain.model.MomentMetadata
import com.mochame.app.domain.repository.telemetry.ObservationActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * ObservationBridge: SQLite-backed implementation of [ObservationActions].
 * * Uses [DateTimeUtils] to calculate biological anchors and
 * [TelemetryDao] for persistent storage.
 */
internal class ObservationBridge(
    private val dao: TelemetryDao,
    private val dateTimeUtils: DateTimeUtils
) : ObservationActions {

    override suspend fun logMoment(draft: MomentDraft) = withContext(Dispatchers.IO) {
        val now = dateTimeUtils.now().toEpochMilliseconds()

        // 1. The Midnight Rule: Calculate biological day using the 4 AM Anchor
        val biologicalDay = dateTimeUtils.calculateBiologicalEpochDay(dateTimeUtils.now())

        // 2. Space Enrichment: Fallback to Space defaults if user didn't specify
        val finalBiophilia = if (draft.detail.biophiliaScale == null && draft.spaceId != null) {
            dao.getSpaceById(draft.spaceId)?.defaultBiophilia
        } else {
            draft.detail.biophiliaScale
        }

        val finalDetail = draft.detail.copy(biophiliaScale = finalBiophilia)

        // 3. Simple Molecule Assembly
        val newMoment = Moment(
            id = uuid4().toString(),
            domainId = draft.domainId,
            topicId = draft.topicId,
            spaceId = draft.spaceId,
            core = draft.core,
            detail = finalDetail,
            context = MomentClimate(), // Initialized as null/default
            metadata = MomentMetadata(
                timestamp = now,
                associatedEpochDay = biologicalDay,
                lastModified = now
            )
        )

        // 4. Persistence via Mapper
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
}