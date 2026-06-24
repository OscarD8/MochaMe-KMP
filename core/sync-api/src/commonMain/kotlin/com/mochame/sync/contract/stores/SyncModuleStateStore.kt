package com.mochame.sync.contract.stores

import com.mochame.sync.contract.models.HLC

interface SyncModuleStateStore {

    suspend fun updateHlcFloor(module: String, hlc: HLC)

    suspend fun stampModuleMetadata(
        module: String,
        watermark: String?,
        timestamp: Long,
    )

//    suspend fun stampWatermark(module: MochaModule, syncId: String, newWatermark: String)
}