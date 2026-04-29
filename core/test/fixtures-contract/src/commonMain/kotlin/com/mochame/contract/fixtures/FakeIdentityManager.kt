package com.mochame.contract.fixtures

import com.mochame.contract.identity.IdentityManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeIdentityManager(
    var stubbedId: String = "fake-node-id"
) : IdentityManager {

    val lock = Mutex()

    suspend fun forceId(newId: String) {
        lock.withLock {
            stubbedId = newId
        }
    }

    override suspend fun getOrCreateNodeId(): String {
        return lock.withLock {
            stubbedId
        }
    }
}