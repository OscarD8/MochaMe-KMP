package com.mochame.sync.fixtures

import com.mochame.utils.fixtures.TestHlcFactory
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.spi.models.SyncIntent
import com.mochame.sync.data.SyncIntentEntity
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal fun createTestSyncIntent(
    hlc: HLC = TestHlcFactory.create(),
    candidateKey: Long = 1000L,
    context: FeatureContext.Type = FeatureContext.Type.BIO_DAILY_CONTEXT,
    payload: ByteArray? = byteArrayOf(0x00),
    status: SyncStatus = SyncStatus.PENDING,
    createdAt: Long = 0L,
    syncId: String? = null,
    leasedAt: Long? = null,
    overflowBlobId: String? = null,
    featureSchemaVersion: Int = 1,
    op: MutationOp = MutationOp.UPSERT,
    retryCount: Int = 0
) = SyncIntent(
    featureSchemaVersion = featureSchemaVersion,
    hlc = hlc,
    candidateKey = candidateKey,
    featureContext = FeatureContext.fromModelString(context.modelName),
    operation = op,
    syncStatus = status,
    retryCount = retryCount,
    createdAt = createdAt,
    payload = payload,
    leasedAt = leasedAt,
    syncId = syncId,
    overflowBlobId = overflowBlobId,
)

internal fun createTestIntentEntity(
    hlc: HLC = TestHlcFactory.create(),
    status: SyncStatus = SyncStatus.PENDING,
    candidateKey: Long = 1000L,
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

internal fun assertDecodedIntentParity(expected: SyncIntent, actual: SyncIntent) {
    assertEquals(expected.featureSchemaVersion, actual.featureSchemaVersion)
    assertEquals(expected.hlc, actual.hlc)
    assertEquals(expected.candidateKey, actual.candidateKey)
    assertEquals(expected.featureContext.featureName, actual.featureContext.featureName)
    assertEquals(expected.featureContext.modelName, actual.featureContext.modelName)
    assertEquals(expected.operation, actual.operation)
    assertEquals(expected.overflowBlobId, actual.overflowBlobId)
    assertEquals(expected.createdAt, actual.createdAt)
    assertEquals(SyncStatus.RECEIVED, actual.syncStatus)
    assertEquals(0, actual.retryCount, "retryCount must reset to 0 upon decode")

    if (expected.payload == null) {
        assertEquals(null, actual.payload)
    } else {
        assertContentEquals(expected.payload, actual.payload)
    }
}

internal fun testBatch(size: Int = 1): List<SyncIntent> = List(size) { index ->
    createTestSyncIntent(candidateKey = index.toLong())
}