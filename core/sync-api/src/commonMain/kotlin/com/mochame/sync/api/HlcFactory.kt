package com.mochame.sync.api

import com.mochame.sync.api.models.HLC


interface HlcFactory {
    suspend fun hydrate(lastKnownHlc: String?, currentNodeId: String): HLC
    suspend fun getNextHlc(): HLC
    suspend fun witness(remoteHlc: HLC)
    fun isValid(hlc: HLC): Boolean
}