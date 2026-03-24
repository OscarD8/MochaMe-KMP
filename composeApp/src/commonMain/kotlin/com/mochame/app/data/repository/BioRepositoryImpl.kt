package com.mochame.app.data.repository

import co.touchlab.kermit.Logger
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.HLC
import com.mochame.app.core.HlcFactory
import com.mochame.app.core.HlcParseException
import com.mochame.app.core.MochaModule
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.MochaDatabase
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.dao.sync.MutationLedgerDao
import com.mochame.app.database.dao.sync.SyncMetadataDao
import com.mochame.app.database.entity.DailyContextEntity
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.repository.sync.LocalFirstRepository
import com.mochame.app.domain.system.BootState
import com.mochame.app.domain.system.BootStatusProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class BioRepositoryImpl(
    private val dateTimeUtils: DateTimeUtils,
    private val bioDao: BioDao,
    logger: Logger,
    bootStatusProvider: BootStatusProvider,
    metadataDao: SyncMetadataDao,
    database: MochaDatabase,
    hlcFactory: HlcFactory,
    ledgerDao: MutationLedgerDao,
) : LocalFirstRepository<DailyContext>(
    hlcFactory = hlcFactory,
    database = database,
    ledgerDao = ledgerDao,
    metadataDao = metadataDao,
    moduleName = MochaModule.BIO,
    logger = logger,
    provider = bootStatusProvider
), BioRepository {     // syncGateway still to come

    override suspend fun initializeDay(
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean
    ): DailyContext {
        val mochaDay = dateTimeUtils.getMochaDay()
        val id = mochaDay.toString()

        return persistUpsert(
            candidateKey = id,
            mergeLogic = { newHlc ->
                val existing = bioDao.getContextById(id)

                merge(
                    id,
                    mochaDay,
                    newHlc,
                    sleepHours,
                    readinessScore,
                    isNapped,
                    existing
                )
            },
            saveAction = { stampedEntity ->
                 bioDao.upsert(stampedEntity.toEntity())
            }
        )
    }

    override suspend fun deleteContext(epochDay: Long): Int {
        val id = epochDay.toString()

        return persistDelete(
            candidateKey = id,
            deleteAction = { newHlc ->
                bioDao.markAsDeleted(
                    id = id,
                    newHlc = newHlc.toString(),
                    timestamp = newHlc.ts
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
            // Delegates to the 'Smart Resolver' in the DAO
            bioDao.resolveSync(remote.toEntity())
        }
    }

    // --- HELPERS ---
    private fun merge(
        newId: String,
        currentDay: Long,
        hlc: HLC,
        sleepHours: Double,
        readinessScore: Int,
        isNapped: Boolean,
        existing: DailyContextEntity?
    ): DailyContext {
        val existingDomain = try {
            existing?.toDomain()
        } catch (e: HlcParseException) {
            logger.e(e) { "Corruption in record ${existing?.id}. Overwriting." }
            throw e
        }

        return (existingDomain?.copy(
            sleepHours = sleepHours,
            hlc = hlc,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = false
        ) ?: DailyContext(
            id = newId,
            epochDay = currentDay,
            hlc = hlc,
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            isNapped = isNapped,
            isDeleted = false
        ))
    }
}