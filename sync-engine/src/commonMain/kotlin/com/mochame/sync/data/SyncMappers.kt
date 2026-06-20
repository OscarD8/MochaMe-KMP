package com.mochame.sync.data

import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.contract.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.SyncModuleStateEntity
import com.mochame.sync.domain.model.SyncIntent
import com.mochame.sync.domain.model.SyncModuleState


/**
 * Database Entity -> Domain Model
 */
fun SyncModuleStateEntity.toDomain(): SyncModuleState {
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
fun SyncModuleState.toEntity(): SyncModuleStateEntity {
    return SyncModuleStateEntity(
        module = module,
        serverWatermark = serverWatermark,
        moduleMaxHlc = localMaxHlc,
        syncId = activeSyncId,
        lastServerSyncTime = lastServerSyncTime,
        lastLocalMutationTime = lastLocalMutationTime
    )
}



fun SyncIntentEntity.toDomain(): SyncIntent {
    return SyncIntent(
        hlc = HLC.parse(hlc),
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

fun SyncIntent.toEntity(): SyncIntentEntity {
        return SyncIntentEntity(
            hlc = hlc.toString(),
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