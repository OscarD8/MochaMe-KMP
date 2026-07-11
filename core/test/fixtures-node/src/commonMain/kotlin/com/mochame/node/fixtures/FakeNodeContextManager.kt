package com.mochame.node.fixtures

import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.node.NodeContext
import com.mochame.sync.spi.node.NodeContextManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeNodeContextManager(
    private val defaultNodeId: String = "fake-node-static-uuid"
) : NodeContextManager {

    // --- State Control (Seeding & Overrides) ---
    var seededContext: NodeContext? = null
    var forcedNextNodeId: String? = null

    // --- State Inspection Telemetry ---
    val updatedHlcFloors = mutableListOf<HLC>()
    val recognizedServerResponses = mutableListOf<Pair<String, Long>>()

    val mutex = Mutex()

    var getOrEstablishCallCount = 0
        private set
    var setAppVersionCallCount = 0
        private set

    private fun getOrInitialize(baseVersion: Int = 0): NodeContext {
        val current = seededContext ?: NodeContext(
            nodeId = forcedNextNodeId ?: defaultNodeId,
            appVersion = baseVersion,
            lastServerSyncTime = null,
            maxHlc = null,
            lastServerWatermark = null,
            lastLocalMutationTime = null
        )
        seededContext = current
        return current
    }

    fun reset() {
        seededContext = null
        forcedNextNodeId = null
        updatedHlcFloors.clear()
        recognizedServerResponses.clear()
        getOrEstablishCallCount = 0
        setAppVersionCallCount = 0
    }

    // --- Interface Implementation ---

    override suspend fun getOrEstablishContext(baseVersion: Int): NodeContext {
        mutex.withLock {
            getOrEstablishCallCount++
            return getOrInitialize(baseVersion)
        }
    }

    override suspend fun setAppVersion(targetVersion: Int) {
        setAppVersionCallCount++
        seededContext = getOrInitialize(targetVersion).copy(appVersion = targetVersion)
    }

    override suspend fun getLastBootedAppVersion(): Int? =
        seededContext?.appVersion

    override suspend fun getLastServerSyncTime(): Long? =
        seededContext?.lastServerSyncTime

    override suspend fun getLastLocalMutationTime(): Long? =
        seededContext?.lastLocalMutationTime

    override suspend fun getNodeId(): String? =
        seededContext?.nodeId ?: forcedNextNodeId ?: defaultNodeId

    override suspend fun getMaxHlc(): HLC? = seededContext?.maxHlc

    override suspend fun updateHlcFloor(hlc: HLC) {
        updatedHlcFloors.add(hlc)
        seededContext = getOrInitialize().copy(maxHlc = hlc)
    }

    override suspend fun overwriteContext(nodeContext: NodeContext) {
        getOrInitialize()
    }

    override suspend fun recogniseServerResponse(watermark: String, timestamp: Long) {
        recognizedServerResponses.add(watermark to timestamp)
        seededContext = getOrInitialize().copy(
            lastServerWatermark = watermark,
            lastServerSyncTime = timestamp
        )
    }
}