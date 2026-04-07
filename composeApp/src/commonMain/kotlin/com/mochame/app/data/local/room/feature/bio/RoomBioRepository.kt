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
import com.mochame.app.infrastructure.logging.appendTag
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.utils.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.log

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
    blobStore: BlobStager
) : LocalFirstRepository<DailyContext>(
    hlcFactory = hlcFactory,
    moduleName = MochaModule.BIO,
    logger = logger.withTag(TAG),
    provider = bootStatusProvider,
    mutationLedger = mutationLedger,
    transactor = transactor,
    metadataStore = metadataStore,
    executor = executor,
    blobStore = blobStore,
    encoder = encoder
), BioRepository {     // syncGateway still to come

    companion object {
        const val TAG = "BioRepo"
    }

    override suspend fun initializeDay(
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean
    ): DailyContext {
        val mochaDay = dateTimeUtils.getMochaDay()
        val id = mochaDay.toString()

        return dispatchIntent(
            candidateKey = id,
            op = MutationOp.UPSERT,
            fetchOldState = { bioDao.getContextById(id)?.toDomain() },
            computeAndStamp = { old, newHlc ->
                merge(
                    id,
                    mochaDay,
                    newHlc,
                    sleepHours,
                    readinessScore,
                    isNapped,
                    newHlc.ts,
                    old
                )
            },
            persist = { stamped ->
                bioDao.upsert(stamped.toEntity())
                return@dispatchIntent stamped
            }
        )
    }

    override suspend fun deleteContext(epochDay: Long): Int {
        val id = epochDay.toString()

        return dispatchIntent(
            candidateKey = id,
            op = MutationOp.DELETE,
            fetchOldState = {
                bioDao.getContextById(id)
                    ?.takeIf { !it.isDeleted }
                    ?.toDomain()
            },
            computeAndStamp = { old, newHlc ->
                old?.copy(isDeleted = true, hlc = newHlc, lastModified = newHlc.ts)
            },
            persist = { tombstone ->
                bioDao.markAsDeleted(
                    tombstone.id,
                    tombstone.hlc.toString(),
                    tombstone.hlc.ts
                )
            }
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
    override suspend fun fetchForSync(entityIds: List<String>): List<DailyContext> {
        // Hydrates the full domain objects (including the isDeleted flag) for the server
        return entityIds.mapNotNull { id ->
            bioDao.getContextById(id)?.toDomain()
        }
    }

    // --- GATEWAY IMPLEMENTATION ---
    override suspend fun storeRemoteChanges(changes: List<DailyContext>) {
        changes.forEach { remote ->
            bioDao.resolveSync(remote.toEntity())
        }
    }

    // --- HELPERS ---
    private fun merge(
        newId: String,
        currentDay: Long,
        newHlc: HLC,
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean,
        modificationTime: Long,
        existing: DailyContext?
    ): DailyContext {
        return (existing?.copy(
            sleepHours = sleepHours,
            hlc = newHlc,
            readinessScore = readinessScore,
            isNapped = isNapped,
            lastModified = modificationTime,
            isDeleted = false
        ) ?: DailyContext(
            id = newId,
            epochDay = currentDay,
            hlc = newHlc,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = false,
            lastModified = modificationTime
        ))
    }
}