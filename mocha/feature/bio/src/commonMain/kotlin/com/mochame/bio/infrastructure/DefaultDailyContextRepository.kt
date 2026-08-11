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
        val draftContext = DailyContext(
            id = mochaDay,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped
        )

        return localUpsert(
            candidateKey = mochaDay,
            computeChange = { existing -> compactState(draftContext, existing) }
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


/*
The Tombstone Trap in deleteContext - SHOULD BE FIXED


fetchOldState = {
    bioDao.getContextById(id)
        ?.takeIf { !it.isDeleted } // ⚠
        ?.toDomain()
}

The Failure Flow

Imagine this exact timeline playing out across your system:

    Local Action: The user deletes a daily context locally. deleteContext executes. The row in the database is updated to isDeleted = true with a high local HLC of, say, 100.

    Remote Incoming Sync: A remote sync packet arrives from the server. It contains an old modification for this exact same daily context, generated on a different device that went offline yesterday. Its HLC is older, say, 090.

    The Engine Executes: Your processRemoteIntent function triggers and calls processIntent.

    The Fetch Phase: processIntent invokes your lambda: fetchOldState().

    The Bug Triggers: bioDao.getContextById(id) successfully finds the local tombstone row. But your code hits ?.takeIf { !it.isDeleted }. Because the row is deleted, this evaluates to null.

    The LWW Check is Bypassed: Back inside your central processIntent engine, it receives oldState = null. It looks at its conflict resolution safety check:
    Kotlin

    if (isRemote && oldState != null && incomingHlc <= oldState.hlc) { ... }

    Because oldState is null, this whole safety block is completely skipped. Your engine has no idea a tombstone even existsInCommitted, nor what its HLC was.

    Ghost Resurrection: The engine assumes this is a brand-new entity insert. It accepts the old remote update (090), overwrites your local tombstone, and flips isDeleted back to false. Your deleted data has been resurrected by an older update.

The Fix

Your central repository engine must be allowed to see the tombstone and its HLC to protect the boundary. You should pass the entity through regardless of its deletion state, and let your conflict or business logic handle the flags:
Kotlin

fetchOldState = {
    bioDao.getContextById(id)?.toDomain() // Allow the engine to see the tombstone's HLC
}
 */