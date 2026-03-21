package com.mochame.app.data.mapper

import com.mochame.app.core.SyncStatus
import com.mochame.app.database.entity.SyncMetadataEntity
import com.mochame.app.domain.model.SyncMetadata


/**
 * Database Entity -> Domain Model
 */
fun SyncMetadataEntity.toDomain(): SyncMetadata {
    return SyncMetadata(
        module = moduleName,
        serverWatermark = serverWatermark,
        localMaxHlc = localMaxHlc,
        activeSyncId = activeSyncId,
        status = syncStatus,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}

/**
 * Domain Model -> Database Entity
 */
fun SyncMetadata.toEntity(): SyncMetadataEntity {
    return SyncMetadataEntity(
        moduleName = module,
        serverWatermark = serverWatermark,
        localMaxHlc = localMaxHlc,
        activeSyncId = activeSyncId,
        syncStatus = status,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}