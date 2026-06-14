package com.mochame.sync.domain.components

import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.domain.model.DecodeContext

/**
 *
 */
interface SyncReceiver {
    val module: MochaModule
    suspend fun processRemoteIntent(decodeContext: DecodeContext, payload: ByteArray?, blobId: String?)
}