package com.mochame.metadata.test

import com.mochame.metadata.GlobalMetadataStore

class FakeGlobalMetaStore : GlobalMetadataStore {
    var storedId: String? = null
    override suspend fun getDeviceId(): String? {
        return storedId
    }
    override suspend fun saveNodeId(newId: String) {
        storedId = newId
    }
}