package com.mochame.app.data.repository

import com.benasher44.uuid.Uuid
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.core.HlcFactory
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.dao.SyncMetadataDao
import com.mochame.app.di.DispatcherProvider
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import com.mochame.app.domain.sync.SyncGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BioRepositoryImpl(
    private val bioDao: BioDao,
    private val metadataDao: SyncMetadataDao, // For watermarks
    private val hlcFactory: HlcFactory,       // For Causality
    private val dateTimeUtils: DateTimeUtils,
    private val dispatchers: DispatcherProvider
) : BioRepository, SyncGateway<DailyContext> {

    private val bioMutex = Mutex()

    // --- BIO REPOSITORY (UI FACING) ---
    override fun getMochaDay(): Long {
        // Correcting the call to use the hardened utility pattern
        return dateTimeUtils.calculateBiologicalEpochDay(dateTimeUtils.now())
    }

    override fun getTodaysContext(): Flow<DailyContext?> {
        return bioDao.observeContext(getMochaDay())
            .map { it?.toDomain() }
    }

    /**
     * Hardened Initialization:
     * Ensures "The Cup" has a stable ID and respects the "Last Modified" heartbeat.
     */
    override suspend fun initializeDay(
        sleepHours: Double,
        readinessScore: Int
    ) = bioMutex.withLock {
        withContext(Dispatchers.IO) {
            val epochDay = getMochaDay()

            // 1. Check if the "Cup" already exists for this biological day
            val existingContext = bioDao.getContextByDay(epochDay)

            val contextToSave = existingContext?.
            toDomain()?.copy( // Update existing record, preserving the stable ID
                sleepHours = sleepHours,
                readinessScore = readinessScore,
                lastModified = dateTimeUtils.now().toEpochMilliseconds()
            )
                ?: // Create a new anchor for this biological day
                DailyContext(
                    id = Uuid.randomUUID().toString(),
                    epochDay = epochDay,
                    sleepHours = sleepHours,
                    readinessScore = readinessScore,
                    lastModified = dateTimeUtils.now().toEpochMilliseconds()
                )

            bioDao.upsert(contextToSave.toEntity())
        }
    }

    override suspend fun upsertContext(context: DailyContext) {
        val newContext = context.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        bioDao.upsert(newContext.toEntity())
    }

    override fun getHistory(): Flow<List<DailyContext>> {
        return bioDao.observeAllContexts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Resilient Deletion:
     * Removes the context but leaves the moments intact for potential 'Soft Recovery'.
     */
    override suspend fun deleteContext(epochDay: Long) = withContext(Dispatchers.IO) {
        bioDao.deleteContext(epochDay)
    }


    // --- SYNC GATEWAY (COORDINATOR FACING) ---

    override suspend fun getPendingUploads(): List<DailyContext> {
        return bioDao.getPendingUploads().map { it.toDomain() }
    }

    override suspend fun lockForSync(ids: List<String>, sessionId: String) {
        // Move from PENDING (1) to SYNCING (2) and tag with ID
        bioDao.updateSyncStatus(ids, oldStatus = 1, newStatus = 2, sessionId = sessionId)
    }

    override suspend fun ingestRemoteChanges(changes: List<DailyContext>, newWatermark: String) {
        bioDao.withTransaction {
            changes.forEach { remote ->
                val local = bioDao.getById(remote.id)

                // THE DIRTY MERGE: Only update if Remote is "newer" (LWW)
                if (local == null || remote.lastModified > local.lastModified) {
                    // PRESERVE DIRTY STATUS: If local was edited, keep it as PENDING
                    val targetStatus = if (local?.syncStatus == 1) 1 else 0
                    bioDao.upsert(remote.copy(syncStatus = targetStatus).toEntity())
                }
            }
            metadataDao.updateWatermark("bio_module", newWatermark)
        }
    }

    override suspend fun resolveAck(sessionId: String, wasSuccessful: Boolean) {
        if (wasSuccessful) {
            // THE ATOMIC ACK: Only clear if still status 2 and matches session
            bioDao.clearSyncedRecords(sessionId)
        } else {
            // RECOVERY: Move back to PENDING (1)
            bioDao.resetSyncingRecords(sessionId)
        }
    }
}