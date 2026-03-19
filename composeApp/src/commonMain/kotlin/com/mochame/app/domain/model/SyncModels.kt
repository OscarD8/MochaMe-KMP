package com.mochame.app.domain.model

import com.mochame.app.core.SyncStatus

data class SyncMetadata(
    val module: String,
    val watermark: String?,
    val status: SyncStatus,
    val lastSync: Long
)