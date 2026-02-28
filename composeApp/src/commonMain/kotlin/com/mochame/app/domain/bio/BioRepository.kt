package com.mochame.app.domain.bio

import kotlinx.coroutines.flow.Flow

/**
 * The Contract: Defines how the app interacts with biological context.
 * This is pure Kotlin; no Room or SQL knowledge allowed here.
 */
interface BioRepository {
    /**
     * Observes the context for a specific day to react to sleep updates.
     */
    fun getContextForDay(epochDay: Long): Flow<DailyContext?>

    /**
     * Saves or updates the daily context (The "Initialization" Story).
     */
    suspend fun saveContext(context: DailyContext)
}