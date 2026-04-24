package com.mochame.sync.domain

import com.mochame.metadata.MochaModule
import com.mochame.sync.domain.model.EntityMetadata

interface SyncReceiver {
    val module: MochaModule
    suspend fun processRemoteChange(metadata: EntityMetadata, payload: ByteArray)
}