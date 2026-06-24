package com.mochame.sync.contract.stores

import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import kotlinx.coroutines.flow.Flow

interface SyncIntentStore {
    suspend fun getPendingByPrimaryKey(
        candidateKey: String,
        module: String
    ): SyncIntent?

    suspend fun recordIntent(entry: SyncIntent)
    suspend fun getPendingByModule(module: String): List<SyncIntent?>
    suspend fun discardIntent(hlc: HLC)
    suspend fun observePendingCount(): Flow<Int>
    suspend fun claimBatch(sessionId: String, limit: Int = 50): Int
    suspend fun getClaimedBatch(sessionId: String): List<SyncIntent>

    suspend fun acknowledgeSuccess(hlcList: List<String>)
    suspend fun stampLastError(hlcs: List<String>, message: String)
}