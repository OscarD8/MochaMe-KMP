package com.mochame.node.managers

import co.touchlab.kermit.Logger
import com.mochame.annotations.IoContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.node.data.NodeContextDao
import com.mochame.node.data.toDomain
import com.mochame.node.data.toEntity
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.node.IdGenerator
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.node.NodeId
import com.mochame.utils.toDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

@Single(binds = [NodeContextManager::class])
class DefaultNodeContextManager(
    private val dao: NodeContextDao,
    private val idGenerator: IdGenerator,
    @IoContext private val ioContext: CoroutineContext,
    private val mutex: Mutex = Mutex(),
    logger: Logger
) : NodeContextManager {

    private val logger = logger.withTags(
        layer = LogTags.Layer.ORCH,
        domain = LogTags.Domain.NODE,
        className = "SrNode"
    )

    @Volatile
    private var cachedContext: NodeContext? = null

    /**
     * Guarantees a node identity exists in the database and applies the provided app version if a new
     * node context is triggered.
     *
     * @param baseVersion Defaults to 0.
     */
    override suspend fun getOrEstablishContext(baseVersion: Int): NodeContext =
        withContext(ioContext) {
            cachedContext?.let { return@withContext it }

            mutex.withLock {
                cachedContext?.let { return@withLock it }

                val entity = dao.getOrEstablish(
                    fallbackId = idGenerator.nextId(),
                    baseVersion = baseVersion,
                    createdAt = Clock.System.now().toEpochMilliseconds()
                ).also {
                    logger.i { "Node Fetched. Id: ${it.nodeId} | V: ${it.appVersion} | Est: ${it.createdAt.toDateTime()}" }
                }

                entity.toDomain().also { domainContext ->
                    cachedContext = domainContext
                }
            }
        }

    override suspend fun setAppVersion(targetVersion: Int) =
        withContext(ioContext) {
            mutex.withLock {
                dao.setVersion(targetVersion)
                cachedContext = cachedContext?.copy(appVersion = targetVersion)
            }
        }

    override suspend fun updateHlcFloor(hlc: HLC) =
        withContext(ioContext) {
            mutex.withLock {
                val rowsUpdated = dao.setMaxHlc(hlc.toString())
                if (rowsUpdated == 0) {
                    logger.d { "HLC floor update ignored. Stored value is already newer than $hlc." }
                } else {
                    cachedContext = cachedContext?.copy(maxHlc = hlc)
                }
            }
        }

    override suspend fun recogniseServerResponse(
        watermark: Long,
        timestamp: Long
    ) = withContext(ioContext) {
        mutex.withLock {
            dao.setWatermarkAndTimestamp(watermark, timestamp)
            cachedContext = cachedContext?.copy(
                lastServerWatermark = watermark,
                lastServerSyncTime = timestamp
            )
        }
    }

    override suspend fun getLastBootedAppVersion(): Int? =
        cachedContext?.appVersion ?: withContext(ioContext) {
            dao.getLastBootedVersion()
        }

    override suspend fun getLastServerSyncTime(): Long? =
        cachedContext?.lastServerSyncTime ?: withContext(ioContext) {
            dao.getLastServerSyncTime()
        }

    override suspend fun getLastLocalMutationTime(): Long? =
        cachedContext?.lastLocalMutationTime ?: withContext(ioContext) {
            dao.getLastLocalMutationTime()
        }

    override suspend fun getLastWatermark(): Long? =
        cachedContext?.lastServerWatermark ?: withContext(ioContext) {
            dao.getLastWatermark()
        }

    override suspend fun getNodeId(): NodeId? =
        cachedContext?.nodeId ?: withContext(ioContext) {
            dao.getNodeId()?.let { NodeId.parse(it) }
        }

    override suspend fun getMaxHlc(): HLC? =
        cachedContext?.maxHlc ?: withContext(ioContext) {
            dao.getMaxHlc()?.let { HLC.parse(it) }
        }

    override suspend fun overwriteNodeContext(nodeContext: NodeContext) =
        withContext(ioContext) {
            mutex.withLock {
                dao.insertOrReplaceContext(nodeContext.toEntity())
                cachedContext = nodeContext
            }
        }
}