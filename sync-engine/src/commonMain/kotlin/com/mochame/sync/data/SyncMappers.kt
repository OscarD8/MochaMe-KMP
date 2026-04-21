package com.mochame.sync.data

import com.mochame.sync.data.entities.SyncMetadataEntity
import com.mochame.sync.domain.model.SyncMetadata


/**
 * Database Entity -> Domain Model
 */
fun SyncMetadataEntity.toDomain(): SyncMetadata {
    return SyncMetadata(
        module = module,
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
        module = module,
        serverWatermark = serverWatermark,
        localMaxHlc = localMaxHlc,
        syncId = activeSyncId,
        syncStatus = status,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}