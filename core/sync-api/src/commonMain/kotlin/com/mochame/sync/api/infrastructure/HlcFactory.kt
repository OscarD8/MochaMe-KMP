package com.mochame.sync.api.infrastructure

import com.mochame.sync.api.models.HLC


interface HlcFactory {
    suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC
    suspend fun getNextHlc(): HLC
    suspend fun witness(remoteHlc: HLC)
    fun isValid(hlc: HLC): Boolean
}