package com.mochame.sync.data

import com.mochame.sync.contract.FeatureContext
import com.mochame.sync.contract.models.FeatureSyncState
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.data.entities.SyncIntentEntity
import com.mochame.sync.data.entities.FeatureSyncStateEntity
import com.mochame.sync.contract.models.SyncIntent


internal fun FeatureSyncStateEntity.toDomain() = FeatureSyncState(
    feature = FeatureContext.fromString(feature),
    serverWatermark = serverWatermark,
    maxHlc = maxHlc?.let { HLC.parse(it) },
    syncId = syncId,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)

internal fun FeatureSyncState.toEntity(): FeatureSyncStateEntity = FeatureSyncStateEntity(
    feature = feature.featureName,
    serverWatermark = serverWatermark,
    maxHlc = maxHlc.toString(),
    syncId = syncId,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)


internal fun SyncIntentEntity.toDomain(): SyncIntent = SyncIntent(
    hlc = HLC.parse(hlc),
    featureSchemaVersion = featureSchemaVersion,
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

internal fun SyncIntent.toEntity(): SyncIntentEntity = SyncIntentEntity(
    hlc = hlc.toString(),
    featureSchemaVersion = featureSchemaVersion,
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
