package com.mochame.server.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object ServerConfig {
    const val PORT = 8080
    const val HOST = "0.0.0.0"

    const val MAX_BACKFILL_THRESHOLD = 1500L
    const val MAX_BACKFILL_CHUNK_SIZE = 500
    const val MAX_BATCH_SIZE = 50

    const val LOG_PRUNE_CHUNK_SIZE: Int = 500
    val LOG_RETENTION_DURATION: Duration = 45.days
    val LOG_PRUNE_INTERVAL: Duration = 24.hours
}