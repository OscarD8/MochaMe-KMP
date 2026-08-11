package com.mochame.sync.api.hlc

import com.mochame.sync.spi.node.NodeId


interface HlcFactory {
    suspend fun hydrate(lastKnownHlc: HLC?, currentNodeId: NodeId): HLC
    suspend fun getNextHlc(): HLC
    suspend fun witness(remoteHlc: HLC)
    suspend fun getCurrentHlc(): HLC?
    fun assertValid(hlc: HLC, contextKey: Long? = null)
}