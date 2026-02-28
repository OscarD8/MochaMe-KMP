package com.mochame.app.data.telemetry

import com.mochame.app.domain.telemetry.Category
import com.mochame.app.domain.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TelemetryRepositoryImpl(
    private val telemetryDao: TelemetryDao
) : TelemetryRepository {

    override fun observeActiveCategories(): Flow<List<Category>> {
        return telemetryDao.observeActiveCategories().map { entities ->
            entities.map { it.toDomain() } // Use the mapper!
        }
    }

    override suspend fun saveCategory(category: Category) {
        // The Domain doesn't care about 'lastModified', so we add it here.
        telemetryDao.upsertCategory(category.toEntity())
    }
}