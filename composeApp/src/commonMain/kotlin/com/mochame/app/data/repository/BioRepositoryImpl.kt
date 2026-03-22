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
import com.mochame.app.database.entity.DailyContextEntity
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.sync.BaseRepository
import com.mochame.app.domain.repository.sync.SyncGateway
import com.mochame.app.domain.repository.sync.SyncJanitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BioRepositoryImpl(
    private val dateTimeUtils: DateTimeUtils,
    private val bioDao: BioDao,
    logger: Logger,
    metadataDao: SyncMetadataDao,
    database: MochaDatabase,
    hlcFactory: HlcFactory,
    ledgerDao: MutationLedgerDao,
    janitor: SyncJanitor
) : BaseRepository<DailyContext>(
    hlcFactory = hlcFactory,
    database = database,
    ledgerDao = ledgerDao,
    metadataDao = metadataDao,
    moduleName = MochaModule.BIO,
    janitor = janitor,
    logger = logger
), BioRepository {
    // syncGateway still to come
    override suspend fun initializeDay(
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean
    ): DailyContext {
        val mochaDay = dateTimeUtils.getMochaDay()
        val id = mochaDay.toString()

        return commitWithIntent(candidateKey = id, op = MutationOp.UPSERT) { newHlc ->
            val hlc = HLC.parse(newHlc)

            // 1. CONVERGENCE: Fetch the current local state
            val existing = bioDao.getContextById(id)

            // 2. MERGE: Preserve remote changes while applying local intent
            val contextToSave = existing?.toDomain()?.copy(
                sleepHours = sleepHours,
                readinessScore = readinessScore,
                isNapped = isNapped,
                isDeleted = false
            ) ?: DailyContext(
                id = id,
                epochDay = mochaDay,
                sleepHours = sleepHours,
                readinessScore = readinessScore,
                isNapped = isNapped,
            )

            // 3. STAMP: Apply the logical pulse
            val stamped = contextToSave
                .withHlc(newHlc)
                .withPhysicalTime(hlc.ts)

            bioDao.upsert(stamped.toEntity())
            stamped
        }
    }

    override suspend fun deleteContext(epochDay: Long): Int {
        val id = epochDay.toString()

        return commitWithIntent(candidateKey = id, op = MutationOp.DELETE) { newHlc ->
            val hlc = HLC.parse(newHlc)

            // Auditor Fix: The DAO MUST return rows affected (1 or 0)
            // to allow 'isGhostDelete' check to work correctly.
            bioDao.markAsDeleted(
                id = id,
                newHlc = newHlc,
                timestamp = hlc.ts
            )
        }
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
            // Delegates to the 'Smart Resolver' in the DAO
            bioDao.resolveSync(remote.toEntity())
        }
    }
}