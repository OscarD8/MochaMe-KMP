package com.mochame.sync.contract.stores

import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent

interface SyncIntentStore {
    suspend fun getPendingByPrimaryKey(candidateKey: String): SyncIntent?

    suspend fun recordIntent(entry: SyncIntent)
    suspend fun getPendingByModule(module: String): List<SyncIntent?>
    suspend fun discardIntent(hlc: HLC)
    suspend fun claimBatch(id: String, limit: Int = 50): Int
    suspend fun getClaimedBatch(id: String): List<SyncIntent>

    suspend fun acknowledgeSuccess(hlcList: List<HLC>)
    suspend fun stampLastError(hlcs: List<String>, message: String)
}