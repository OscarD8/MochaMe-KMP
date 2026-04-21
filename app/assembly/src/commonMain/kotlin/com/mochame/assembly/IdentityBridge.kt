package com.mochame.assembly

import com.mochame.orchestrator.IdentityManager
import com.mochame.sync.domain.providers.SyncUserProvider

class IdentityBridge(
    private val identityManager: IdentityManager
) : SyncUserProvider {

    override suspend fun getOrCreateNodeId(): String {
        return identityManager.getOrCreateNodeId()
    }
}