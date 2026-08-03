package com.mochame.sync.api.hlc


interface HlcFactory {
    suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: String): HLC
    suspend fun getNextHlc(): HLC
    suspend fun witness(remoteHlc: HLC)
    suspend fun getCurrentHlc(): HLC?
    fun assertValid(hlc: HLC, contextKey: String? = null)
}