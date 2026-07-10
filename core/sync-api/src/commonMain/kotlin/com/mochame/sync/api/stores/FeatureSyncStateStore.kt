package com.mochame.sync.api.stores

import com.mochame.sync.api.models.NodeSyncState
import com.mochame.sync.api.models.HLC

interface FeatureSyncStateStore {

    suspend fun getFeatureMetadata(module: String): NodeSyncState?

    suspend fun updateHlcFloor(module: String, hlc: HLC)

    suspend fun stampFeatureMetadata(
        module: String,
        watermark: String?,
        timestamp: Long,
    )

//    suspend fun stampWatermark(module: MochaModule, syncId: String, newWatermark: String)
}