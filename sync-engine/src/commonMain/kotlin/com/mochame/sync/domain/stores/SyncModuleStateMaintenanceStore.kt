package com.mochame.sync.domain.stores


internal interface SyncModuleStateMaintenanceStore {

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
