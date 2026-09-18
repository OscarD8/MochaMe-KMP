package com.mochame.sync.spi.models

data class ClaimedBatch(
    val batchId: Long,
    val intents: List<SyncIntent>
)