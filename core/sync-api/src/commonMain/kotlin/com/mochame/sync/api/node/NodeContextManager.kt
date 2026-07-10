package com.mochame.sync.api.node

import com.mochame.sync.api.models.HLC

interface NodeContextManager {
    suspend fun getOrCreateNodeId(): String
    suspend fun getGlobalMaxHlc(): HLC?
    suspend fun initializeContext(currentVersion: Int): NodeContext


}