package com.mochame.server.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Thresholds and chunk sizes, network bindings, channel capacities, and backfill parameters.
 */
interface ServerConfig {
    val port: Int
    val host: String

    val maxBackfillThreshold: Long
    val maxBackfillChunkSize: Int
    val maxBroadcastingBatchSize: Int

    val logPruneChunkSize: Int
    val logRetentionDuration: Duration
    val logPruneInterval: Duration

    val writeChannelCapacity: Int
    val outboundChannelCapacity: Int
    val outboundStagingCapacity: Int

    companion object Default : ServerConfig {
        override val port: Int = 8080
        override val host: String = "0.0.0.0"
        override val maxBackfillThreshold: Long = 1500L
        override val maxBackfillChunkSize: Int = 500
        override val maxBroadcastingBatchSize: Int = 50
        override val logPruneChunkSize: Int = 500
        override val logRetentionDuration: Duration = 45.days
        override val logPruneInterval: Duration = 24.hours
        override val writeChannelCapacity: Int = 1000
        override val outboundChannelCapacity: Int = 256
        override val outboundStagingCapacity: Int = 512
    }
}