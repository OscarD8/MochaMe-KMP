package com.mochame.app.domain.system.sync

import com.mochame.app.domain.system.sync.utils.MochaModule
import com.mochame.app.infrastructure.sync.HLC

interface MetadataStore {
    suspend fun recordMetadata(moduleName: MochaModule, hlc: HLC)

}

interface MetadataStoreMaintenance {
    suspend fun bulkResetDirtyModules(): Int

    suspend fun getDirtyModuleNames(): List<String>

    suspend fun getMetadataCount(): Int

    /**
     * Ensures all MochaModules have a corresponding metadata row.
     * Infrastructure handles mapping and technical checks.
     */
    suspend fun ensureSeeded(): Int
    suspend fun getGlobalMaxHlc(): String?

}
