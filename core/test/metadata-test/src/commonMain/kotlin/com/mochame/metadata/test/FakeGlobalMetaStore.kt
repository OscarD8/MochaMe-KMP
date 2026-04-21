package com.mochame.metadata.test

import com.mochame.metadata.GlobalMetadataStore
import kotlinx.coroutines.yield

class FakeGlobalMetaStore : GlobalMetadataStore {
    var storedId: String? = null
    override suspend fun getDeviceId(): String? {
        yield()
        return storedId
    }
    override suspend fun saveNodeId(newId: String) {
        yield()
        storedId = newId
    }
}