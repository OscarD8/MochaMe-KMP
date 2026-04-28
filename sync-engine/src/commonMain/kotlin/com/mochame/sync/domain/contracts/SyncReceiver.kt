package com.mochame.sync.domain.contracts

import com.mochame.contract.metadata.MochaModule
import com.mochame.sync.domain.model.EntityMetadata

interface SyncReceiver {
    val module: MochaModule
    suspend fun processRemoteChange(metadata: EntityMetadata, payload: ByteArray)
}