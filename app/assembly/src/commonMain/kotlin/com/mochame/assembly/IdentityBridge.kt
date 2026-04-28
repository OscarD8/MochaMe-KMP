package com.mochame.assembly

import com.mochame.logic.IdentityManager
import com.mochame.sync.domain.providers.SyncUserProvider
import org.koin.core.annotation.Single

@Single(binds = [SyncUserProvider::class])
class IdentityBridge(
    private val identityManager: IdentityManager
) : SyncUserProvider {
    override suspend fun getOrCreateNodeId(): String {
        return identityManager.getOrCreateNodeId()
    }
}