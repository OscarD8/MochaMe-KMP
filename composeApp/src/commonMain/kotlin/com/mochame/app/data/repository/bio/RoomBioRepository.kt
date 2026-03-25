package com.mochame.app.data.repository.bio

import co.touchlab.kermit.Logger
import com.mochame.app.data.common.LocalFirstRepository
import com.mochame.app.data.mappers.toDomain
import com.mochame.app.data.mappers.toEntity
import com.mochame.app.data.local.room.dao.BioDao
import com.mochame.app.data.local.room.entity.DailyContextEntity
import com.mochame.app.domain.bio.BioRepository
import com.mochame.app.domain.bio.DailyContext
import com.mochame.app.domain.exceptions.HlcParseException
import com.mochame.app.domain.sync.MetadataStore
import com.mochame.app.domain.sync.MutationLedger
import com.mochame.app.domain.sync.TransactionProvider
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC
import com.mochame.app.infrastructure.sync.HlcFactory
import com.mochame.app.infrastructure.system.boot.BootStatusProvider
import com.mochame.app.infrastructure.utils.DateTimeUtils
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
) : LocalFirstRepository<DailyContext>(
    hlcFactory = hlcFactory,
    moduleName = MochaModule.BIO,
    logger = logger,
    provider = bootStatusProvider,
    mutationLedger = mutationLedger,
    transactor = transactor,
    metadataStore = metadataStore
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
        val existingDomain = try { // NOTE have not implemented anywhere catching this
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