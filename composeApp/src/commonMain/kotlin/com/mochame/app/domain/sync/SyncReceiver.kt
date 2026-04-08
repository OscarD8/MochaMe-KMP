package com.mochame.app.domain.sync

import com.mochame.app.domain.sync.model.EntityMetadata
import com.mochame.app.domain.sync.utils.MochaModule

interface SyncReceiver {
    val module: MochaModule
    suspend fun processRemoteChange(metadata: EntityMetadata, payload: ByteArray)
}