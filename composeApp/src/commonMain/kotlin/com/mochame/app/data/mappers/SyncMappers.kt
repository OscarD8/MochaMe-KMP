package com.mochame.app.data.mappers

import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.domain.sync.model.SyncMetadata


/**
 * Database Entity -> Domain Model
 */
fun SyncMetadataEntity.toDomain(): SyncMetadata {
    return SyncMetadata(
        module = moduleName,
        serverWatermark = serverWatermark,
        localMaxHlc = localMaxHlc,
        activeSyncId = syncId,
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
        syncId = activeSyncId,
        syncStatus = status,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}