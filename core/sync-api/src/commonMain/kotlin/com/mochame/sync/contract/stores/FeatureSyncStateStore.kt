package com.mochame.sync.contract.stores

import com.mochame.sync.contract.models.FeatureSyncState
import com.mochame.sync.contract.models.HLC

interface FeatureSyncStateStore {

    suspend fun getFeatureMetadata(module: String): FeatureSyncState?

    suspend fun updateHlcFloor(module: String, hlc: HLC)

    suspend fun stampFeatureMetadata(
        module: String,
        watermark: String?,
        timestamp: Long,
    )

//    suspend fun stampWatermark(module: MochaModule, syncId: String, newWatermark: String)
}