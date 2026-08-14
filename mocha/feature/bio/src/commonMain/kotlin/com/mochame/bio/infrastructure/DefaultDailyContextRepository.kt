package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.data.BioDao
import com.mochame.bio.data.toDomain
import com.mochame.bio.data.toEntity
import com.mochame.bio.domain.DailyContext
import com.mochame.bio.domain.DailyContextRepository
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.api.repository.LocalFirstRepository
import com.mochame.sync.common.TriState
import com.mochame.utils.interfaces.MochaTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single


@Single([DailyContextRepository::class])
internal class DefaultDailyContextRepository(
    private val timeUtils: MochaTimeProvider,
    private val bioDao: BioDao,
    codecRouter: DailyContextCodecRouter,
    logger: Logger,
    deps: LocalFirstDependencies
) : LocalFirstRepository<DailyContext>(
    FeatureContext.Type.BIO_DAILY_CONTEXT,
    deps,
    codecRouter,
    logger = logger.withTags(LogTags.Layer.REPO, LogTags.Domain.BIO, "BioRepo")
), DailyContextRepository {

    override suspend fun establishDay(
        sleepHours: Double,
        readinessScore: Int,
        isNapped: TriState
    ): Long {
        val mochaDay = timeUtils.getMochaDay()
        val draftState = DailyContext(
            id = mochaDay,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped
        )

        return localUpsert(
            candidateKey = mochaDay,
            computeChange = { existing -> compactState(draftState, existing) }
        )
    }

    override suspend fun deleteContext(epochDay: Long) = localDelete(candidateKey = epochDay)

    override fun observeContext(epochDay: Long): Flow<DailyContext?> =
        bioDao.observeContext(epochDay).map { it?.toDomain() }


    // --- MAINTENANCE / SYNC ---
    override suspend fun hardDelete(cutoff: Long) = bioDao.hardDeletePruning(cutoff)
    override suspend fun getTombstoneCount() = bioDao.getTombstoneCount()
    override suspend fun fetch(id: Long) = bioDao.getContextById(id)?.toDomain()
    override suspend fun save(entity: DailyContext) = bioDao.upsert(entity.toEntity())
    override suspend fun compactState(
        newState: DailyContext,
        existing: DailyContext?
    ): DailyContext = existing?.copy(
        sleepHours = newState.sleepHours,
        readinessScore = newState.readinessScore,
        isNapped = newState.isNapped,
        lastModified = newState.lastModified
    ) ?: DailyContext(
        id = newState.id,
        sleepHours = newState.sleepHours,
        readinessScore = newState.readinessScore,
        isNapped = newState.isNapped,
        lastModified = newState.lastModified
    )
}

