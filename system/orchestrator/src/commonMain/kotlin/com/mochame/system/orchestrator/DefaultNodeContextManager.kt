package com.mochame.system.orchestrator

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.di.IoContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.contract.node.NodeContextStore
import com.mochame.contract.node.IdGenerator
import com.mochame.contract.node.NodeContext
import com.mochame.contract.node.NodeContextManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Single(binds = [NodeContextManager::class])
class DefaultNodeContextManager(
    @Provided private val nodeStore: NodeContextStore,
    @Provided private val idGenerator: IdGenerator,
    @Provided @IoContext private val ioContext: CoroutineContext,
    @IdentityMutex private val mutex: Mutex,
    @Provided logger: Logger
) : NodeContextManager {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.NODE,
        className = "NodeMr"
    )

    override suspend fun getOrCreateNodeId(): String = withContext(ioContext) {
        mutex.withLock { resolveNodeId() }
    }

    /**
     * Called by the Janitor on boot. Guarantees identity exists and stamps
     * the current app version atomically under the same lock acquisition —
     * avoids the double-lock deadlock of calling getOrCreateNodeId() internally.
     */
    override suspend fun initializeContext(currentVersion: Int): NodeContext =
        withContext(ioContext) {
            mutex.withLock {
                val id = resolveNodeId()
                val previous = nodeStore.getLastBootedVersion() ?: 0

                nodeStore.setVersion(currentVersion)
                logger.i { "Node Boot Context: ID=$id, Delta=$previous -> $currentVersion" }

                NodeContext(nodeId = id, baselineVersion = previous)
            }
        }

    /**
     * Non-locking resolution — must only be called from within an
     * existing mutex.withLock block.
     */
    private suspend fun resolveNodeId(): String {
        logger.v { "Resolving node ID..." }

        nodeStore.getNodeId()?.let { existing ->
            logger.d { "Node ID recovered: $existing" }
            return existing
        }

        val newId = idGenerator.nextId()
        logger.i { "First boot — generating node ID: $newId" }
        nodeStore.saveNodeId(newId)
        logger.i { "Node ID persisted" }
        return newId
    }

}