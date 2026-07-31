package com.mochame.bio.domain

import com.mochame.sync.common.TriState
import kotlinx.coroutines.flow.Flow

interface DailyContextRepository {

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
    suspend fun establishDay(sleepHours: Double, readinessScore: Int, isNapped: TriState) : DailyContext

//    /**
//     * Historical record for long-term efficiency analysis.
//     */
//    fun getHistory(): Flow<List<DailyContext>>

    /**
     * Resilient Deletion:
     * Removes the 'Cup' (BioContext) but leaves the 'Brew' (Moments) intact.
     * This allows for "Soft Recovery" if the day is re-initialized.
     */
    suspend fun deleteContext(epochDay: String): Int

//    suspend fun upsertContext(context: DailyContext)

    suspend fun hardDelete(cutoff: Long)
    suspend fun getTombstoneCount(): Int
}