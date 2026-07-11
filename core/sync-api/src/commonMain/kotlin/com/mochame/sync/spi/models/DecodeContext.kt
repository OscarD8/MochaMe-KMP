package com.mochame.sync.spi.models

import com.mochame.sync.api.metadata.MutationOp
import com.mochame.sync.api.models.HLC

data class DecodeContext(
    val featureSchemaVersion: Int,
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)
