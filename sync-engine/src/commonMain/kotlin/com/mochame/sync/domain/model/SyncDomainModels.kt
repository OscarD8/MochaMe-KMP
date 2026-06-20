package com.mochame.sync.domain.model


import com.mochame.contract.metadata.MochaModule
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.HLC
import com.mochame.sync.domain.state.SyncStatus
import com.mochame.sync.infrastructure.LocalFirstRepository
import com.mochame.sync.data.daos.SyncIntentDao

data class SyncModuleState(
    val module: MochaModule,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)

data class SyncIntent(
    val hlc: HLC,
    val candidateKey: String,
    val module: MochaModule,
    val model: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val syncId: String?,
    val payload: ByteArray?,
    val diagnosticSummary: String?,
    val overflowBlobId: String?,
    val leasedAt: Long?,
    val retryCount: Int,
    val createdAt: Long,
    val lastErrorMessage: String?
)

/**
 * Fields existing in the synced payload that are required to reconstruct the model payload itself.
 * Currently used for decoding a payload in [LocalFirstRepository].
 */
data class DecodeContext(
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)

/**
 * Container designed to hold a module and its count of quarantined records.
 * Passed back by [SyncIntentDao.observeQuarantinedCountByModule] and utilized by
 * the UI can observe the flow defined in SyncIntentStore, and display a simple
 * count of failed local intents.
 */
data class QuarantinedModuleSummary(
    val module: MochaModule,
    val count: Int
)

/**
 * Server side object for wrapping [SyncIntentResult] objects, to enable
 * diagnosis of individual intents of a passed batch, meanwhile allowing
 * the server to accept valid intents.
 */
data class SyncBatchResponse(
    val results: List<SyncIntentResult>
)

/**
 * Passed back by the server, wrapped in a [SyncBatchResponse].
 * Holds whether an individual intent (identified by the HLC) was accepted
 * on the server side and any error message if not.
 */
data class SyncIntentResult(
    val hlc: String,
    val accepted: Boolean,
    val errorMessage: String? = null
)