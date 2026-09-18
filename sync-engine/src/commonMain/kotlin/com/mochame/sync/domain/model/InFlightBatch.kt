package com.mochame.sync.domain.model

import kotlinx.coroutines.CompletableDeferred

data class InFlightBatch (
    val batchId: Long,
    val deferred: CompletableDeferred<Long>
)