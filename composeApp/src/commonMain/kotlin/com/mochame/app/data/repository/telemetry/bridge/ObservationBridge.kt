package com.mochame.app.data.repository.telemetry.bridge

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.TelemetryDao
import com.mochame.app.domain.model.Moment
import com.mochame.app.domain.repository.telemetry.ObservationActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * ObservationBridge: SQLite-backed implementation of [ObservationActions].
 * * Uses [DateTimeUtils] to calculate biological anchors and
 * [TelemetryDao] for persistent storage.
 */
internal class ObservationBridge(
    private val dao: TelemetryDao,
    private val dateTimeUtils: DateTimeUtils
) : ObservationActions {

    override suspend fun logMoment(
        domainId: String,
        satisfactionScore: Int,
        moodScore: Int,
        energyScore: Int,
        topicId: String?,
        spaceId: String?,
        note: String?,
        isFocusTime: Boolean?,
        socialScale: Int?,
        energyDrain: Int?,
        biophiliaScale: Int?,
        durationMinutes: Int?
    ) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. The Midnight Rule: Calculate biological day using the 4 AM Anchor
        val biologicalDay = dateTimeUtils.calculateBiologicalEpochDay(Clock.System.now())

        // 2. Space Enrichment: Fallback to Space defaults if user didn't specify
        val finalBiophilia = if (biophiliaScale == null && spaceId != null) {
            dao.getSpaceById(spaceId)?.defaultBiophilia
        } else {
            biophiliaScale
        }

        // 3. Assemble the Molecule
        val newMoment = Moment(
            id = uuid4().toString(),
            domainId = domainId,
            satisfactionScore = satisfactionScore,
            moodScore = moodScore,
            energyScore = energyScore,
            topicId = topicId,
            spaceId = spaceId,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            isFocusTime = isFocusTime,
            socialScale = socialScale,
            energyDrain = energyDrain,
            biophiliaScale = finalBiophilia,
            durationMinutes = durationMinutes,
            // Environment context initialized as null (WorkManager/API fills these later)
            isDaylight = null,
            cloudDensity = null,
            isPrecipitating = null,
            timestamp = now,
            associatedEpochDay = biologicalDay,
            lastModified = now
        )

        // 4. Persistence via Mapper
        dao.upsertMoment(newMoment.toEntity())
    }

    override suspend fun saveMoment(moment: Moment) = withContext(Dispatchers.IO) {
        val updatedMoment = moment.copy(
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        dao.upsertMoment(updatedMoment.toEntity())
    }

    override suspend fun deleteMoment(momentId: String) = withContext(Dispatchers.IO) {
        dao.deleteMomentById(momentId)
    }
}