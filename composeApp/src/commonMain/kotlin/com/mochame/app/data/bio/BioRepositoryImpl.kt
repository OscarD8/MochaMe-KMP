package com.mochame.app.data.bio

import com.mochame.app.domain.bio.DailyContext
import com.mochame.app.domain.bio.BioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BioRepositoryImpl(
    private val bioDao: BioDao
) : BioRepository {

    override fun getContextForDay(epochDay: Long): Flow<DailyContext?> {
        return bioDao.getContextForDay(epochDay).map { entity ->
            entity?.toDomain() // Map Room Entity -> Clean Domain Model
        }
    }

    override suspend fun saveContext(context: DailyContext) {
        // Map Clean Domain Model -> Room Entity with a fresh timestamp
        bioDao.upsertContext(context.toEntity())
    }
}