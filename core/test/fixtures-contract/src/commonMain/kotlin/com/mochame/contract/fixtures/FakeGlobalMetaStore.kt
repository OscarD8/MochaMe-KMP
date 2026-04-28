package com.mochame.contract.fixtures

import com.mochame.contract.identity.GlobalMetadataStore

class FakeGlobalMetaStore : GlobalMetadataStore {
    var storedId: String? = null
    override suspend fun getDeviceId(): String? {
        return storedId
    }
    override suspend fun saveNodeId(newId: String) {
        storedId = newId
    }
}