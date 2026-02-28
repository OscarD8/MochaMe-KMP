package com.mochame.app.domain.telemetry

import kotlinx.coroutines.flow.Flow

interface TelemetryRepository {
    fun observeActiveCategories(): Flow<List<Category>>
    suspend fun saveCategory(category: Category)
}