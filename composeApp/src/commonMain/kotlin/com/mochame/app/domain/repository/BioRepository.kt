package com.mochame.app.domain.repository

import com.mochame.app.domain.model.DailyContext
import kotlinx.coroutines.flow.Flow

interface BioRepository {

    /**
     * Calculates the current epoch day based on the 4:00 AM rollover.
     */
    fun getMochaDay(): Long

    /**
     * Initialization Interceptor:
     * Observes the context for the current biological day.
     * If null, the UI triggers the "Pour Your Fuel" modal.
     */
    fun getTodaysContext(): Flow<DailyContext?>

    /**
     * The Day Starter (Smart Constructor):
     * Handles UUID generation and biological anchoring internally.
     * Prevents the UI from accidentally creating "dirty" or duplicate entries.
     */
    suspend fun initializeDay(sleepHours: Double, readinessScore: Int)

    /**
     * Historical 'Fuel' record for long-term efficiency analysis.
     */
    fun getHistory(): Flow<List<DailyContext>>

    /**
     * Resilient Deletion:
     * Removes the 'Cup' (BioContext) but leaves the 'Brew' (Moments) intact.
     * This allows for "Soft Recovery" if the day is re-initialized.
     */
    suspend fun deleteContext(epochDay: Long)

    suspend fun upsertContext(context: DailyContext)
}