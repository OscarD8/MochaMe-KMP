package com.mochame.app.data.repository

import com.benasher44.uuid.uuid4
import com.mochame.app.core.DateTimeUtils
import com.mochame.app.data.mapper.toDomain
import com.mochame.app.data.mapper.toEntity
import com.mochame.app.database.dao.BioDao
import com.mochame.app.domain.model.DailyContext
import com.mochame.app.domain.repository.BioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import kotlin.time.Clock

class BioRepositoryImpl(
    private val bioDao: BioDao,
    private val dateTimeUtils: DateTimeUtils
) : BioRepository {


    /**
     * The Master Clock Authority:
     * Enforces the 4:00 AM Midnight Rule globally.
     */
    override fun getCurrentBioDay(): Long {
        TODO("This should refer to the global tool of datetime utils")

//        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
//        val epochDays = now.date.toEpochDays()
//        return if (now.hour < 4) epochDays - 1 else epochDays
    }

    override fun getTodaysContext(): Flow<DailyContext?> {
        return bioDao.getContextByEpochDay(getCurrentBioDay()).map { it?.toDomain() }
    }

    /**
     * Non-Blocking Initialization:
     * Creates the (BioContext) without touching existing (Moments).
     */
    override suspend fun initializeDay(
        sleepHours: Double,
        readinessScore: Int
    ) = withContext(Dispatchers.IO) {
        val newContext = DailyContext(
            id = uuid4().toString(),
            epochDay = getCurrentBioDay(),
            sleepHours = sleepHours,
            readinessScore = readinessScore,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        bioDao.upsertDailyContext(newContext.toEntity())
    }

    override fun getHistory(): Flow<List<DailyContext>> {
        return bioDao.getAllContextsFlow().map { entities ->
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