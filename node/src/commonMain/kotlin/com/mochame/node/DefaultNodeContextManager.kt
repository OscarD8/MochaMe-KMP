package com.mochame.node

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.NodeManagerMutex
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.NodeContextEntity
import com.mochame.node.data.toDomain
import com.mochame.node.data.toEntity
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.spi.node.NodeContextManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Single(binds = [NodeContextManager::class])
class DefaultNodeContextManager(
    @Provided private val dao: NodeContextDao,
    private val idGenerator: IdGenerator,
    @IoContext private val ioContext: CoroutineContext,
    @NodeManagerMutex private val mutex: Mutex,
    logger: Logger
) : NodeContextManager {
    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.NODE,
        className = "SrNode"
    )

    /**
     * Guarantees a node identity exists and applies the provided app version if a new
     * node context is triggered.
     *
     * @param baseVersion Defaults to 0.
     */
    override suspend fun getOrEstablishContext(baseVersion: Int): NodeContext =
        withContext(ioContext) {
            mutex.withLock {
                val node = dao.getContext() ?: NodeContextEntity(
                    nodeId = idGenerator.nextId(),
                    appVersion = baseVersion
                ).also { newNode ->
                    dao.upsert(newNode)
                    logger.d { "Node Context not found. Established Node [${newNode.nodeId} | Version: ${newNode.appVersion}]" }
                }

                logger.i { "Node Context: ID=${node.nodeId} | Version: ${node.appVersion}" }

                node.toDomain()
            }
        }

    override suspend fun setAppVersion(targetVersion: Int) = dao.setVersion(targetVersion)

    override suspend fun getLastBootedAppVersion() = dao.getLastBootedVersion()
    override suspend fun getLastServerSyncTime() = dao.getLastServerSyncTime()
    override suspend fun getLastLocalMutationTime() = dao.getLastLocalMutationTime()
    override suspend fun getNodeId() = dao.getNodeId()
    override suspend fun getMaxHlc() = dao.getMaxHlc()?.let { HLC.parse(it) }

    override suspend fun updateHlcFloor(hlc: HLC) = dao.setMaxHlc(hlc.toString())

    override suspend fun recogniseServerResponse(
        watermark: String,
        timestamp: Long
    ) = dao.setWatermarkAndTimestamp(watermark, timestamp)

    override suspend fun overwriteContext(nodeContext: NodeContext) =
        dao.insertOrReplaceContext(nodeContext.toEntity())

}