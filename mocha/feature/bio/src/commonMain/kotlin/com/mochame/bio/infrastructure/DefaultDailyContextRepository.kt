package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.data.DailyContextDao
import com.mochame.bio.data.toDomain
import com.mochame.bio.data.toEntity
import com.mochame.bio.domain.DailyContext
import com.mochame.bio.domain.DailyContextRepository
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.api.repository.LocalFirstRepository
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.utils.interfaces.MochaTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.time.Instant


@Single([DailyContextRepository::class, SyncReceiver::class])
internal class DefaultDailyContextRepository(
    private val timeUtils: MochaTimeUtils,
    @Provided private val dailyContextDao: DailyContextDao,
    codecRouter: DailyContextCodecRouter,
    logger: Logger,
    @Provided deps: LocalFirstDependencies
) : LocalFirstRepository<DailyContext>(
    FeatureContext.BIO_DAILY_CONTEXT,
    deps,
    codecRouter,
    logger = logger.withTags(LogTags.Layer.REPO, LogTags.Domain.BIO, "BioRep")
), DailyContextRepository {

    override suspend fun upsertDay(
        sleepHours: Double?,
        readinessScore: Int?,
        isNapped: Boolean?
    ): Long {
        val mochaDay = timeUtils.getMochaDay()
        val draftContext = DailyContext(
            id = mochaDay,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            createdAt = Instant.fromEpochMilliseconds(mochaDay),
            isNapped = isNapped
        )

        return localUpsert(candidateKey = mochaDay) { existing ->
            compactState(draftContext, existing)
        }
    }

    override suspend fun deleteContext(epochDay: Long) = localDelete(candidateKey = epochDay)

    override fun observeContext(epochDay: Long): Flow<DailyContext?> =
        dailyContextDao.observeContext(epochDay).map { it?.toDomain() }


    // --- MAINTENANCE / SYNC ---
    override suspend fun hardDelete(cutoff: Long) = dailyContextDao.hardDeletePruning(cutoff)
    override suspend fun getTombstoneCount() = dailyContextDao.getTombstoneCount()
    override suspend fun fetch(id: Long) = dailyContextDao.getContextById(id)?.toDomain()
    override suspend fun save(entity: DailyContext) = dailyContextDao.upsert(entity.toEntity())
    override suspend fun compactState(
        newState: DailyContext,
        existing: DailyContext?
    ): DailyContext = existing?.copy(
        sleepHours = newState.sleepHours,
        readinessScore = newState.readinessScore,
        isNapped = newState.isNapped,
        lastModified = newState.lastModified,
        createdAt = existing.createdAt
    ) ?: DailyContext(
        id = newState.id,
        sleepHours = newState.sleepHours,
        readinessScore = newState.readinessScore,
        isNapped = newState.isNapped,
        lastModified = newState.lastModified,
        createdAt = newState.createdAt
    )
}

