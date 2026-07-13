package com.mochame.node

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IoContext
import com.mochame.contract.di.NodeManagerMutex
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.toDomain
import com.mochame.node.data.toEntity
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.utils.toDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Manager acts as a pass through from domain layers to the database, expecting domain
 * models and passing back domain models for the [NodeContext]. Atomicity and
 * causal logic for [NodeContext.maxHlc] has been delegated to the [NodeContextDao],
 * but calls to assert a node context and update HLC state are wrapped by the [NodeManagerMutex].
 * It is expected that many of the methods provided should be called within transactions
 * that can roll back.
 */
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
            val entity = dao.getOrEstablish(
                fallbackId = idGenerator.nextId(),
                baseVersion = baseVersion,
                createdAt = Clock.System.now().toEpochMilliseconds()
            ).also {
                logger.i { "Node Fetched. Id: ${it.nodeId} | V: ${it.appVersion} | Est: ${it.createdAt.toDateTime()}" }
            }

            entity.toDomain()
        }

    override suspend fun setAppVersion(targetVersion: Int) = dao.setVersion(targetVersion)

    override suspend fun updateHlcFloor(hlc: HLC) = withContext(ioContext) {
        val rowsUpdated = dao.setMaxHlc(hlc.toString())
        if (rowsUpdated == 0) {
            logger.d { "HLC floor update ignored. Stored value is already newer than $hlc." }
        }
    }

    // Does it need a mutex?
    override suspend fun recogniseServerResponse(
        watermark: String,
        timestamp: Long
    ) = dao.setWatermarkAndTimestamp(watermark, timestamp)

    override suspend fun getLastBootedAppVersion() = dao.getLastBootedVersion()
    override suspend fun getLastServerSyncTime() = dao.getLastServerSyncTime()
    override suspend fun getLastLocalMutationTime() = dao.getLastLocalMutationTime()
    override suspend fun getNodeId() = dao.getNodeId()
    override suspend fun getMaxHlc() = dao.getMaxHlc()?.let { HLC.parse(it) }

    override suspend fun overwriteNodeContext(nodeContext: NodeContext) =
        dao.insertOrReplaceContext(nodeContext.toEntity())

}