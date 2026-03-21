package com.mochame.app.data.repository

import co.touchlab.kermit.Logger
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.HLC
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.MochaModule
import com.mochame.app.core.MutationOp
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.sync.BaseRepository
import com.mochame.app.domain.repository.sync.SyncGateway

class BioRepositoryImpl(
    private val dateTimeUtils: DateTimeUtils,
    private val bioDao: BioDao,
    logger: Logger,
    metadataDao: SyncMetadataDao,
    database: MochaDatabase,
    hlcFactory: HlcFactory,
    ledgerDao: MutationLedgerDao
) : BaseRepository<DailyContext>(
    hlcFactory = hlcFactory,
    database = database,
    ledgerDao = ledgerDao,
    metadataDao = metadataDao,
    moduleName = MochaModule.BIO,
    logger = logger
), BioRepository, SyncGateway<DailyContext> {

    override suspend fun initializeDay(sleepHours: Double, readinessScore: Int): DailyContext {
        val id = dateTimeUtils.getMochaDay().toString()

        return mutate(entityId = id, op = MutationOp.UPSERT) { newHlc ->
            // 1. ABSOLUTE CONVERGENCE: Parse the pulse provided by the engine
            val hlc = HLC.parse(newHlc)
            val existing = bioDao.getContextById(id)

            val contextToSave = existing?.toDomain()?.copy(
                sleepHours = sleepHours,
                readinessScore = readinessScore,
                isDeleted = false
            ) ?: DailyContext(
                id = id,
                epochDay = hlc.ts / (24 * 60 * 60 * 1000), // Optional: Double-check day alignment
                sleepHours = sleepHours,
                readinessScore = readinessScore
            )

            // 2. STAMP: Sync the Logical Pulse and Physical UI Time
            val stamped = contextToSave
                .withHlc(newHlc)
                .withPhysicalTime(hlc.ts)

            bioDao.upsert(stamped.toEntity())
            stamped
        }
    }

    override suspend fun deleteContext(epochDay: Long): Int = mutate(
        entityId = epochDay.toString(),
        op = MutationOp.DELETE
    ) { newHlc ->
        val hlc = HLC.parse(newHlc)
        // markAsDeleted should be defined to return Int (the number of rows affected)
        bioDao.markAsDeleted(
            id = epochDay.toString(),
            newHlc = newHlc,
            timestamp = hlc.ts
        )
    }

    // --- MAINTENANCE (JANITOR FACING) ---

    /**
     * Called by the Janitor during off-peak hours.
     * This does NOT use 'mutate' because pruning is a local cleanup,
     * not a new syncable event.
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

    override suspend fun storeRemoteChanges(changes: List<DailyContext>) {
        changes.forEach { remote ->
            // Delegates to the 'Smart Resolver' in the DAO
            bioDao.resolveSync(remote.toEntity())
        }
    }
}