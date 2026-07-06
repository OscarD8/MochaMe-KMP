package com.mochame.sync.test.fakes

import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.models.HLC
import com.mochame.sync.contract.models.SyncIntent
import com.mochame.sync.contract.SyncStatus

fun createTestSyncIntent(
    hlc: HLC = HLC.parse("2026-06-22T12:00:00.000Z-0000-0000000000000000"),
    candidateKey: String = "test-key-123",
    context: MochaModuleContext.Type = MochaModuleContext.Type.UNRECOGNIZED_FALLBACK,
    payload: ByteArray? = byteArrayOf(0x00)
): SyncIntent {
    return SyncIntent(
        featureSchemaVersion = 1,
        hlc = hlc,
        candidateKey = candidateKey,
        module = context.moduleName,
        model = context.modelName,
        operation = MutationOp.UPSERT,
        syncStatus = SyncStatus.IDLE,
        retryCount = 0,
        createdAt = 0L,
        payload = payload
    )
}