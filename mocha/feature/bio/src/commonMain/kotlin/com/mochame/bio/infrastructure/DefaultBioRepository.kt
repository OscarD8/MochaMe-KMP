package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.data.BioDao
import com.mochame.bio.data.toDomain
import com.mochame.bio.data.toEntity
import com.mochame.bio.domain.BioRepository
import com.mochame.bio.domain.DailyContext
import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.di.IoContext
import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.contract.policy.ExecutionPolicy
import com.mochame.contract.providers.DateTimeProvider
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.platform.providers.TransactionProvider
import com.mochame.sync.contract.HlcFactory
import com.mochame.sync.domain.components.FeatureCodecRegistry
import com.mochame.sync.domain.stores.BlobStager
import com.mochame.sync.domain.stores.SyncModuleStateStore
import com.mochame.sync.domain.stores.SyncIntentStore
import com.mochame.sync.infrastructure.KeyedLocker
import com.mochame.sync.infrastructure.LocalFirstRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single([BioRepository::class])
class DefaultBioRepository(
    private val dateTimeUtils: DateTimeProvider,
    private val bioDao: BioDao,
    @IoContext ioContext: CoroutineContext,
    logger: Logger,
    bootStatusProvider: BootStatusProvider,
    hlcFactory: HlcFactory,
    syncModuleStateStore: SyncModuleStateStore,
    transactor: TransactionProvider,
    syncIntentStore: SyncIntentStore,
    executor: ExecutionPolicy,
    codec: FeatureCodecRegistry<DailyContext>,
    blobStore: BlobStager,
    locker: KeyedLocker,
) : LocalFirstRepository<DailyContext>(
    hlcFactory = hlcFactory,
    module = MochaModule.Bio.DailyContext,
    provider = bootStatusProvider,
    syncIntentStore = syncIntentStore,
    transactor = transactor,
    syncModuleStateStore = syncModuleStateStore,
    executor = executor,
    blobStore = blobStore,
    locker = locker,
    codecRouter = codec,
    ioContext = ioContext,
    logger = logger.withTags(
        layer = LogTags.Layer.INFRA,
        domain = LogTags.Domain.BIO,
        className = "BioRepo"
    )
), BioRepository {     // syncGateway still to come

    override suspend fun establishDay(
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean
    ): DailyContext {
        val mochaDay = dateTimeUtils.getMochaDay()
        val id = mochaDay.toString()

        return processIntent(
            candidateKey = id,
            op = MutationOp.UPSERT,
            fetchExistingState = { bioDao.getContextById(id)?.toDomain() },
            computeChange = { existing ->
                meldState(
                    id,
                    mochaDay,
                    sleepHours,
                    readinessScore,
                    isNapped,
                    existing
                )
            },
            persist = { stamped ->
                bioDao.upsert(stamped.toEntity())
                return@processIntent stamped
            },
            onSkip = { it!! }
        )
    }

    /**
     * Currently set to return 0 where there was a skip.
     */
    override suspend fun deleteContext(epochDay: Long): Int {
        val id = epochDay.toString()

        return processIntent(
            candidateKey = id,
            op = MutationOp.DELETE,
            fetchExistingState = {
                bioDao.getContextById(id)
                    // Gemini noted possible issue here - check bottom note
                    ?.takeIf { !it.isDeleted }
                    ?.toDomain()
            },
            computeChange = { existing ->
                existing?.copy(isDeleted = true)
            },
            persist = { tombstone ->
                bioDao.markAsDeleted(
                    tombstone.id,
                    tombstone.hlc.toString(),
                    tombstone.hlc.ts
                )
            },
            onSkip = { 0 }
        )
    }

    override fun observeContext(epochDay: Long): Flow<DailyContext?> {
        return bioDao.observeContext(epochDay).map { it?.toDomain() }
    }

    // --- MAINTENANCE ---
    /**
     * Called by the Janitor during off-peak hours.
     * Not a new syncable event.
     */
    override suspend fun pruneOldTombstones(cutoff: Long) {
        bioDao.hardDeletePruning(cutoff)
    }

    override suspend fun getTombstoneCount(): Int = bioDao.getTombstoneCount()

    // --- SYNC GATEWAY ---
    override suspend fun fetch(id: String): DailyContext? {
        return bioDao.getContextById(id)?.toDomain()
    }

    override suspend fun save(entity: DailyContext) {
        bioDao.upsert(entity.toEntity())
    }

    // --- HELPERS ---
    private fun meldState(
        newId: String,
        currentDay: Long,
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean,
        existing: DailyContext?
    ): DailyContext {
        return (existing?.copy(
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = false
        ) ?: DailyContext(
            id = newId,
            epochDay = currentDay,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = false,
        ))
    }
}


/*
The Tombstone Trap in deleteContext


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

    Because oldState is null, this whole safety block is completely skipped. Your engine has no idea a tombstone even exists, nor what its HLC was.

    Ghost Resurrection: The engine assumes this is a brand-new entity insert. It accepts the old remote update (090), overwrites your local tombstone, and flips isDeleted back to false. Your deleted data has been resurrected by an older update.

The Fix

Your central repository engine must be allowed to see the tombstone and its HLC to protect the boundary. You should pass the entity through regardless of its deletion state, and let your conflict or business logic handle the flags:
Kotlin

fetchOldState = {
    bioDao.getContextById(id)?.toDomain() // Allow the engine to see the tombstone's HLC
}
 */