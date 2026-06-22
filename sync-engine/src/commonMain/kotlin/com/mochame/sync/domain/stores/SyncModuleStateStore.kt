package com.mochame.sync.domain.stores

import com.mochame.contract.metadata.MochaModuleContext
import com.mochame.sync.contract.HLC


interface SyncModuleStateStore {

    suspend fun updateHlcFloor(module: String, hlc: HLC)

    suspend fun stampModuleMetadata(
        module: String,
        watermark: String?,
        timestamp: Long,
    )

//    suspend fun stampWatermark(module: MochaModule, syncId: String, newWatermark: String)
}

interface SyncModuleStateMaintenanceStore {

    suspend fun getMetadataCount(): Int

    /**
     * Ensures all MochaModules have a corresponding metadata row.
     * Infrastructure handles mapping and technical checks.
     */
    suspend fun ensureSeeded(): Int

    /**
     * This will represent the max HLC of the local device.
     * In cloud integration, it should probably have some way
     * of working with the Coordinator to ensure this value
     * represents the global truth.
     */
    suspend fun getGlobalMaxHlc(): String?

}
