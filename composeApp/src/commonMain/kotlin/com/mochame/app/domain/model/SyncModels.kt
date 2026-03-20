package com.mochame.app.domain.model

import com.mochame.app.core.SyncStatus

interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: String // This replaced lastModified
    fun withHlc(hlc: String): T
}

data class SyncMetadata(
    val module: String,
    val watermark: String?,
    val status: SyncStatus,
    val lastSync: Long
)