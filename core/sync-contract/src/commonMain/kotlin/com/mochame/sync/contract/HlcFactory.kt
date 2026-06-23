package com.mochame.sync.contract

import com.mochame.sync.contract.models.HLC


interface HlcFactory {
    suspend fun hydrate(lastKnownHlc: String?, currentNodeId: String): HLC
    suspend fun getNextHlc(): HLC
    suspend fun witness(remoteHlc: HLC)
    fun isValid(hlc: HLC): Boolean
}