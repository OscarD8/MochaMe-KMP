package com.mochame.sync.fakes

import com.mochame.support.TestHlcFactory
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.data.SyncIntentEntity

internal fun createTestSyncIntent(
    hlc: HLC,
    candidateKey: String = "test-key-123",
    context: FeatureContext.Type = FeatureContext.Type.BIO_DAILY_CONTEXT,
    payload: ByteArray? = byteArrayOf(0x00),
    status: SyncStatus = SyncStatus.PENDING,
    createdAt: Long = 0L,
    leasedAt: Long? = null
) = SyncIntent(
    featureSchemaVersion = 1,
    hlc = hlc,
    candidateKey = candidateKey,
    featureContext = FeatureContext.fromModelString(context.modelName),
    operation = MutationOp.UPSERT,
    syncStatus = status,
    retryCount = 0,
    createdAt = createdAt,
    payload = payload,
    leasedAt = leasedAt
)

internal fun createTestIntentEntity(
    hlc: HLC = TestHlcFactory.create(),
    status: SyncStatus = SyncStatus.PENDING,
    candidateKey: String = "test-key",
    syncId: String? = null,
    overflowBlobId: String? = null,
    createdAt: Long = TestHlcFactory.BASE_TEST_TIME,
    retryCount: Int = 0,
    leasedAt: Long = TestHlcFactory.BASE_TEST_TIME,
    payload: ByteArray? = byteArrayOf(0x01),
    feature: FeatureContext = FeatureContext.Type.UNRECOGNIZED_MODEL
) = SyncIntentEntity(
    hlc = hlc.toString(),
    featureSchemaVersion = 1,
    candidateKey = candidateKey,
    feature = feature.featureName,
    model = feature.modelName,
    operation = MutationOp.UPSERT,
    payload = payload,
    overflowBlobId = overflowBlobId,
    syncStatus = status,
    syncId = syncId,
    leasedAt = leasedAt,
    diagnosticSummary = null,
    retryCount = retryCount,
    lastErrorMessage = null,
    createdAt = createdAt
)