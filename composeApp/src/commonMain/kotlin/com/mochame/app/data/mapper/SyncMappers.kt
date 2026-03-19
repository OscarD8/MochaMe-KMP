package com.mochame.app.data.mapper

import com.mochame.app.core.SyncStatus
import com.mochame.app.database.entity.SyncMetadataEntity
import com.mochame.app.domain.model.SyncMetadata


fun SyncMetadataEntity.toDomain() = SyncMetadata(
    module = moduleName,
    watermark = lastWatermark,
    status = SyncStatus.fromInt(lastSyncStatus), // Now the compiler is happy
    lastSync = lastSyncTime
)

fun SyncMetadata.toEntity(sessionId: String?) = SyncMetadataEntity(
    moduleName = module,
    lastWatermark = watermark,
    lastSyncStatus = status.value, // Maps the enum back to an Int
    activeSessionId = sessionId,
    lastSyncTime = lastSync
)