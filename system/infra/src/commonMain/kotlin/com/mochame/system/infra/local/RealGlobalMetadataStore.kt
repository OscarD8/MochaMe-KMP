package com.mochame.system.infra.local

import com.mochame.contract.identity.GlobalMetadataStore
import org.koin.core.annotation.Single

@Single(binds = [GlobalMetadataStore::class])
class RealGlobalMetadataStore(
    private val dao: GlobalMetadataDao,
) : GlobalMetadataStore {
    override suspend fun saveNodeId(newId: String)  {
        dao.updateNodeId(newId)
    }

    override suspend fun getDeviceId(): String? =  dao.getDeviceId()
}