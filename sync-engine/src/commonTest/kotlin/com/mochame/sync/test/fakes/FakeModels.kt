package com.mochame.sync.test.fakes

import com.mochame.sync.api.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.support.TestHlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.api.models.SyncIntent
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.data.entities.SyncIntentEntity

internal fun createTestSyncIntent(
    hlc: HLC,
    candidateKey: String = "test-key-123",
    context: FeatureContext.Type = FeatureContext.Type.UNRECOGNIZED_FALLBACK,
    payload: ByteArray? = byteArrayOf(0x00)
) = SyncIntent(
    featureSchemaVersion = 1,
    hlc = hlc,
    candidateKey = candidateKey,
    module = context.featureName,
    model = context.modelName,
    operation = MutationOp.UPSERT,
    syncStatus = SyncStatus.PENDING,
    retryCount = 0,
    createdAt = 0L,
    payload = payload
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
    module: String = FeatureContext.Type.UNRECOGNIZED_FALLBACK.featureName
) = SyncIntentEntity(
    hlc = hlc.toString(),
    featureSchemaVersion = 1,
    candidateKey = candidateKey,
    module = module,
    model = "BioModel",
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
