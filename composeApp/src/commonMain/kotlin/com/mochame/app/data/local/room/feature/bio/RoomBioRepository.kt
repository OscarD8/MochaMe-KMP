package com.mochame.app.data.local.room.feature.bio

import co.touchlab.kermit.Logger
import com.mochame.app.data.common.LocalFirstRepository
import com.mochame.app.data.local.room.dao.BioDao
import com.mochame.app.data.mappers.toDomain
import com.mochame.app.data.mappers.toEntity
import com.mochame.app.domain.feature.bio.BioRepository
import com.mochame.app.domain.feature.bio.DailyContext
import com.mochame.app.domain.sync.PayloadEncoder
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.stores.BlobStager
import com.mochame.app.domain.sync.stores.MetadataStore
import com.mochame.app.domain.sync.stores.MutationLedger
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.domain.sync.utils.MutationOp
import com.mochame.app.domain.system.sqlite.ExecutionPolicy
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.utils.DateTimeUtils
import com.mochame.app.infrastructure.utils.KeyedLocker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBioRepository(
    private val dateTimeUtils: DateTimeUtils,
    private val bioDao: BioDao,
    logger: Logger,
    bootStatusProvider: BootStatusProvider,
    hlcFactory: HlcFactory,
    metadataStore: MetadataStore,
    transactor: TransactionProvider,
    mutationLedger: MutationLedger,
    executor: ExecutionPolicy,
    encoder: PayloadEncoder<DailyContext>,
    blobStore: BlobStager,
    locker: KeyedLocker,
) : LocalFirstRepository<DailyContext>(
    hlcFactory = hlcFactory,
    module = MochaModule.BIO,
    logger = logger.withTag(TAG),
    provider = bootStatusProvider,
    mutationLedger = mutationLedger,
    transactor = transactor,
    metadataStore = metadataStore,
    executor = executor,
    blobStore = blobStore,
    locker = locker,
    encoder = encoder
), BioRepository {     // syncGateway still to come

    companion object {
        const val TAG = "BioRepo"
    }

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
            fetchOldState = { bioDao.getContextById(id)?.toDomain() },
            computeChange = { old ->
                merge(
                    id,
                    mochaDay,
                    sleepHours,
                    readinessScore,
                    isNapped,
                    old
                )
            },
            persist = { stamped ->
                bioDao.upsert(stamped.toEntity())
                return@processIntent stamped
            },
            onSkip = { it!! }
        )
    }

    override suspend fun deleteContext(epochDay: Long): Int {
        val id = epochDay.toString()

        return processIntent(
            candidateKey = id,
            op = MutationOp.DELETE,
            fetchOldState = {
                bioDao.getContextById(id)
                    ?.takeIf { !it.isDeleted }
                    ?.toDomain()
            },
            computeChange = { old ->
                old?.copy(isDeleted = true)
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

    // --- MAINTENANCE (JANITOR FACING) ---
    /**
     * Called by the Janitor during off-peak hours.
     * Not a new syncable event.
     */
    override suspend fun pruneOldTombstones(cutoff: Long) {
        bioDao.hardDeletePruning(cutoff)
    }

    override suspend fun getTombstoneCount(): Int = bioDao.getTombstoneCount()

    // --- SYNC GATEWAY (COORDINATOR FACING) ---
    override suspend fun fetch(id: String): DailyContext? {
        return bioDao.getContextById(id)?.toDomain()
    }

    override suspend fun save(entity: DailyContext) {
        bioDao.upsert(entity.toEntity())
    }

    // --- HELPERS ---
    private fun merge(
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