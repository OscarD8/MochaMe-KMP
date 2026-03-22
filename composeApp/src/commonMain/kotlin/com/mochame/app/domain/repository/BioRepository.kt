package com.mochame.app.domain.repository

import com.mochame.app.domain.model.DailyContext
import kotlinx.coroutines.flow.Flow

interface BioRepository {

    /**
     * Initialization Interceptor:
     * Observes the context for the current biological day.
     * If null, the UI triggers the "Pour Your Fuel" modal.
     */
    fun observeContext(epochDay: Long): Flow<DailyContext?>


    /**
     * The Day Starter (Smart Constructor):
     * Handles UUID generation and biological anchoring internally.
     * Prevents the UI from accidentally creating "dirty" or duplicate entries.
     */
    suspend fun initializeDay(sleepHours: Double, readinessScore: Int, isNapped: Boolean = false) : DailyContext

//    /**
//     * Historical 'Fuel' record for long-term efficiency analysis.
//     */
//    fun getHistory(): Flow<List<DailyContext>>

    /**
     * Resilient Deletion:
     * Removes the 'Cup' (BioContext) but leaves the 'Brew' (Moments) intact.
     * This allows for "Soft Recovery" if the day is re-initialized.
     */
    suspend fun deleteContext(epochDay: Long): Int

//    suspend fun upsertContext(context: DailyContext)

    suspend fun fetchForSync(entityIds: List<String>): List<DailyContext>

    suspend fun storeRemoteChanges(changes: List<DailyContext>)
    suspend fun pruneOldTombstones(cutoff: Long)
    suspend fun getTombstoneCount(): Int
}