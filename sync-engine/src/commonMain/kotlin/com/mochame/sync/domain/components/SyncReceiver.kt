package com.mochame.sync.domain.components

import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.domain.model.EntityMetadata

/**
 *
 */
interface SyncReceiver {
    val module: MochaModule
    suspend fun processRemoteIntent(metadata: EntityMetadata, payload: ByteArray)
}