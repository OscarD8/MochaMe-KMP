package com.mochame.sync.data

import com.mochame.sync.contract.models.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncModuleStateEntity
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.domain.model.SyncModuleState


/**
 * Database Entity -> Domain Model
 */
internal fun SyncModuleStateEntity.toDomain(): SyncModuleState {
    return SyncModuleState(
        module = module,
        serverWatermark = serverWatermark,
        localMaxHlc = moduleMaxHlc,
        activeSyncId = syncId,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}

/**
 * Domain Model -> Database Entity
 */
internal fun SyncModuleState.toEntity(): SyncModuleStateEntity {
    return SyncModuleStateEntity(
        module = module,
        serverWatermark = serverWatermark,
        moduleMaxHlc = localMaxHlc,
        syncId = activeSyncId,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}



internal fun SyncIntentEntity.toDomain(): SyncIntent {
    return SyncIntent(
        hlc = hlc,
        candidateKey = candidateKey,
        module = module,
        model = model,
        operation = operation,
        syncStatus = syncStatus,
        syncId = syncId,
        payload = payload,
        diagnosticSummary = diagnosticSummary,
        overflowBlobId = overflowBlobId,
        retryCount = retryCount,
        createdAt = createdAt,
        leasedAt = leasedAt,
        lastErrorMessage = lastErrorMessage
    )
}

internal fun SyncIntent.toEntity(): SyncIntentEntity {
        return SyncIntentEntity(
            hlc = hlc,
            candidateKey = candidateKey,
            module = module,
            model = model,
            operation = operation,
            syncStatus = syncStatus,
            syncId = syncId,
            payload = payload,
            diagnosticSummary = diagnosticSummary,
            overflowBlobId = overflowBlobId,
            retryCount = retryCount,
            createdAt = createdAt,
            leasedAt = leasedAt,
            lastErrorMessage = lastErrorMessage
        )
    }