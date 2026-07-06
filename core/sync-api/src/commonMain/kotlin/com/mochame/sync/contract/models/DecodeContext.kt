package com.mochame.sync.contract.models

import com.mochame.contract.metadata.MutationOp

data class DecodeContext(
    val featureSchemaVersion: Int,
    val id: String,
    val hlc: HLC,
    val op: MutationOp,
    val lastModified: Long
)
