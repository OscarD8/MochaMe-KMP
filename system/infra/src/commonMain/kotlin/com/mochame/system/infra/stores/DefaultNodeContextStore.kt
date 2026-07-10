package com.mochame.system.infra.stores

import com.mochame.sync.api.node.NodeContext
import com.mochame.sync.api.node.NodeContextStore
import com.mochame.system.infra.data.NodeContextDao
import com.mochame.system.infra.data.toDomain
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single(binds = [NodeContextStore::class])
class DefaultNodeContextStore(
    @Provided private val dao: NodeContextDao,
) : NodeContextStore {

    /**
     * Avoids SQLite read on every sync cycle path.
     * Populated on first read or write, never stale because only this
     * store mutates the nodeId column.
     */
    private var cachedNodeId: String? = null

    override suspend fun getNodeId(): String? {
        cachedNodeId?.let { return it }
        return dao.getNodeId().also { cachedNodeId = it }
    }

    override suspend fun saveNodeId(newId: String) {
        dao.upsertNodeId(newId)
        cachedNodeId = newId
    }

    override suspend fun getLastBootedVersion(): Int? =
        dao.getLastBootedVersion()

    override suspend fun setVersion(version: Int) =
        dao.setVersion(version)

    override suspend fun hasIdentity(): Boolean =
        dao.hasIdentity()

    override suspend fun getContext(): NodeContext? =
        dao.getContext()?.toDomain()
}