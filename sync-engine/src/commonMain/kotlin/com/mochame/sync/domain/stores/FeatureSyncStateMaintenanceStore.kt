package com.mochame.sync.domain.stores


internal interface FeatureSyncStateMaintenanceStore {

    suspend fun getFeatureCount(): Int

    /**
     * Ensures all MochaModules have a corresponding metadata row.
     * Infrastructure handles mapping and technical checks.
     * This is currently necessary to allow simple update calls in the Dao, as stamping
     * module metadata may be a frequent operation.
     * Probably could have done that better.
     */
    suspend fun ensureSeeded(): Int

    /**
     * This will represent the max HLC of the local device.
     * The [com.mochame.sync.contract.LocalFirstRepository] is responsible for
     * ensuring that any local or remote intents for its own model make
     * a call to witness that intents HLC.
     */
    suspend fun getGlobalMaxHlc(): String?

}
