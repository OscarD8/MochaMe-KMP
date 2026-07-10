package com.mochame.sync.contract.models

import com.mochame.sync.contract.FeatureContext

data class FeatureSyncState(
    val feature: FeatureContext,
    val serverWatermark: String? = null,
    val maxHlc: HLC? = null,
    val syncId: String? = null,
    val lastServerSyncTime: Long = 0L,           // Wall-clock of the last successful 200 OK
    val lastLocalMutationTime: Long = 0L         // Wall-clock of the last local HLC generation
)