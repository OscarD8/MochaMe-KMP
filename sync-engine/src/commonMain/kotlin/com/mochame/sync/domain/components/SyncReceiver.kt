package com.mochame.sync.domain.components

import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.sync.domain.model.DecodeContext

/**
 *
 */
interface SyncReceiver {
    val moduleContext: MochaModuleContext

    /**
     * Expects the synced intent to have had its relevant metadata extracted that the feature
     * models themselves hold (all fields listed in [DecodeContext]), the payload of the intent,
     * or the blobId as this is where branching logic is to be established on overflow instances.
     */
    suspend fun processRemoteIntent(decodeContext: DecodeContext, payload: ByteArray?, blobId: String?)
}