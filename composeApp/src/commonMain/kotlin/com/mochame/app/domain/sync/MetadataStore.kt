package com.mochame.app.domain.sync

import com.mochame.app.data.local.room.entity.SyncMetadataEntity
import com.mochame.app.domain.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC

interface MetadataStore {
    suspend fun recordMetadata(moduleName: MochaModule, hlc: HLC)

}

interface MetadataStoreMaintenance {
    suspend fun bulkResetDirtyModules(): Int

    suspend fun getDirtyModuleNames(): List<String>

    suspend fun getMetadataCount(): Int

    suspend fun seedDefaultMetadata(seeds: List<SyncMetadataEntity>)

    suspend fun getGlobalMaxHlc(): String?

}
