package com.mochame.sync.api.models

import com.mochame.sync.api.metadata.MutationOp

data class DecodeContext(
    val featureSchemaVersion: Int,
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)
