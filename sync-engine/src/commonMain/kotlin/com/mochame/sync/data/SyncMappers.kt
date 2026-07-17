package com.mochame.sync.data

import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.models.SyncIntent

internal fun SyncIntentEntity.toDomain(): SyncIntent = SyncIntent(
    hlc = HLC.parse(hlc),
    featureSchemaVersion = featureSchemaVersion,
    candidateKey = candidateKey,
    featureContext = FeatureContext.fromModelString(model),
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
    feature = featureContext.featureName,
    model = featureContext.modelName,
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
