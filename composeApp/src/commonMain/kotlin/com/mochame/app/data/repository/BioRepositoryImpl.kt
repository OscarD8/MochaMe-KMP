package com.mochame.app.data.repository

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.BioDao
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BioRepositoryImpl(
    private val bioDao: BioDao,
    private val dateTimeUtils: DateTimeUtils
) : BioRepository {

    // Guards against concurrent initialization during 4:00 AM state shifts
    private val bioMutex = Mutex()

    override fun getMochaDay(): Long {
        // Correcting the call to use the hardened utility pattern
        return dateTimeUtils.calculateBiologicalEpochDay(dateTimeUtils.now())
    }

    override fun getTodaysContext(): Flow<DailyContext?> {
        return bioDao.observeContextByDay(getMochaDay())
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
                    id = uuid4().toString(),
                    epochDay = epochDay,
                    sleepHours = sleepHours,
                    readinessScore = readinessScore,
                    lastModified = dateTimeUtils.now().toEpochMilliseconds()
                )

            bioDao.insertOrReplace(contextToSave.toEntity())
        }
    }

    override suspend fun upsertContext(context: DailyContext) {
        val newContext = context.copy(
            lastModified = dateTimeUtils.now().toEpochMilliseconds()
        )
        bioDao.insertOrReplace(newContext.toEntity())
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
    override suspend fun deleteContext(id: String) = withContext(Dispatchers.IO) {
        bioDao.deleteContextById(id)
    }
}