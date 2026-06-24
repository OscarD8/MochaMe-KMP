package com.mochame.sync.domain.model


import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.sync.data.daos.SyncIntentDao

/**
 * Container designed to hold a module and its count of quarantined records.
 * Passed back by [SyncIntentDao.observeQuarantinedCountByModule] and utilized by
 * the UI can observe the flow defined in SyncIntentStore, and display a simple
 * count of failed local intents.
 */
internal data class QuarantinedModuleSummary(
    val module: MochaModuleContext,
    val count: Int
)

/**
 * Server side object for wrapping [SyncIntentResult] objects, to enable
 * diagnosis of individual intents of a passed batch, meanwhile allowing
 * the server to accept valid intents.
 */
internal data class SyncBatchResponse(
    val results: List<SyncIntentResult>
)

/**
 * Passed back by the server, wrapped in a [SyncBatchResponse].
 * Holds whether an individual intent (identified by the HLC) was accepted
 * on the server side and any error message if not.
 */
internal data class SyncIntentResult(
    val hlc: String,
    val accepted: Boolean,
    val errorMessage: String? = null
)