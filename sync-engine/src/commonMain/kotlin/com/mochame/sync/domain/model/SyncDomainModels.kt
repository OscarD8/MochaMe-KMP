package com.mochame.sync.domain.model


import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.contract.metadata.MutationOp
import com.mochame.sync.contract.HLC
import com.mochame.sync.domain.state.SyncStatus
import com.mochame.sync.infrastructure.LocalFirstRepository
import com.mochame.sync.data.daos.SyncIntentDao

data class SyncModuleState(
    val module: String,
    val serverWatermark: String?,
    val localMaxHlc: String?,
    val activeSyncId: String?,
    val lastServerSyncTime: Long,
    val lastLocalMutationTime: Long
)

data class SyncIntent(
    val hlc: HLC,
    val candidateKey: String,
    val module: String,
    val model: String,
    val operation: MutationOp,
    val syncStatus: SyncStatus,
    val retryCount: Int,
    val createdAt: Long,
    val syncId: String? = null,
    val payload: ByteArray? = null,
    val diagnosticSummary: String? = null,
    val overflowBlobId: String? = null,
    val leasedAt: Long? = null,
    val lastErrorMessage: String? = null
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
    val module: MochaModuleContext,
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