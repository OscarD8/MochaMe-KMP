package com.mochame.contract.fixtures

import com.mochame.contract.node.NodeContext
import com.mochame.contract.node.NodeContextStore


class FakeNodeContextStore : NodeContextStore {

    var storedNodeId: String? = null
    var storedVersion: Int = 0

    // Failure injection
    var getNodeIdError: Throwable? = null
    var saveNodeIdError: Throwable? = null
    var getLastBootedVersionError: Throwable? = null
    var setVersionError: Throwable? = null
    var hasIdentityError: Throwable? = null
    var hasContextError: Throwable? = null

    override suspend fun getNodeId(): String? {
        getNodeIdError?.let { throw it }
        return storedNodeId
    }

    override suspend fun saveNodeId(newId: String) {
        saveNodeIdError?.let { throw it }
        storedNodeId = newId
    }

    override suspend fun getLastBootedVersion(): Int? {
        getLastBootedVersionError?.let { throw it }
        return storedVersion.takeIf { storedNodeId != null }
    }

    override suspend fun setVersion(version: Int) {
        setVersionError?.let { throw it }
        storedVersion = version
    }

    override suspend fun hasIdentity(): Boolean {
        hasIdentityError?.let { throw it }
        return storedNodeId != null
    }

    override suspend fun getContext(): NodeContext? {
        hasContextError?.let { throw it }
        return storedNodeId?.let { NodeContext(it, baselineVersion = storedVersion) }
    }
}