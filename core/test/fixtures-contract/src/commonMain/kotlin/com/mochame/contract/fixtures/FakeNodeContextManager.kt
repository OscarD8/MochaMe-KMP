package com.mochame.contract.fixtures

import com.mochame.contract.node.NodeContext
import com.mochame.contract.node.NodeContextManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeNodeContextManager : NodeContextManager {

    val mutex = Mutex()

    var storedNodeId: String? = null
    var storedVersion: Int = 0
    var generateId: () -> String = { "fake-node-id" }

    // Failure injection
    var getOrCreateNodeIdError: Throwable? = null
    var initializeContextError: Throwable? = null

    override suspend fun getOrCreateNodeId(): String {
        getOrCreateNodeIdError?.let { throw it }
        return mutex.withLock {
            storedNodeId ?: generateId().also { storedNodeId = it }
        }
    }

    override suspend fun initializeContext(currentVersion: Int): NodeContext {
        initializeContextError?.let { throw it }
        return mutex.withLock {
            val id = storedNodeId ?: generateId().also { storedNodeId = it }
            val previous = storedVersion
            storedVersion = currentVersion
            NodeContext(nodeId = id, baselineVersion = previous)
        }
    }
}