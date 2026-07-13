package com.mochame.sync.spi.node

import com.mochame.sync.api.models.HLC


interface NodeContextManager {

    suspend fun getOrEstablishContext(baseVersion: Int = 0): NodeContext

    suspend fun overwriteNodeContext(nodeContext: NodeContext)

    suspend fun setAppVersion(targetVersion: Int)

    suspend fun getLastBootedAppVersion(): Int?

    suspend fun getNodeId(): String?

    suspend fun getLastServerSyncTime(): Long?

    suspend fun getLastLocalMutationTime(): Long?

    suspend fun updateHlcFloor(hlc: HLC)

    suspend fun recogniseServerResponse(
        watermark: String,
        timestamp: Long,
    )

    suspend fun getMaxHlc(): HLC?

}