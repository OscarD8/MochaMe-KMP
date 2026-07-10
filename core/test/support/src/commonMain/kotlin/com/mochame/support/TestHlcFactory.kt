package com.mochame.support

import com.mochame.sync.api.models.HLC


/**
 * Global provider for generating fixed HLCs.
 */
object TestHlcFactory {

    const val DEFAULT_NODE = "node-test-device"

    /**
     * Fixed base time: Thursday, July 9, 2026
     */
    const val BASE_TEST_TIME = 0L

    /**
     * Builds a standalone HLC with safe defaults.
     */
    fun create(
        ts: Long = BASE_TEST_TIME,
        count: Int = 0,
        nodeId: String = DEFAULT_NODE
    ): HLC = HLC(ts = ts, count = count, nodeId = nodeId)

    /**
     * Generates a list of HLCs with strictly incrementing physical time (ts).
     * Simulates events happening sequentially across different clock ticks.
     */
    fun chronologicalSequence(
        size: Int,
        stepMs: Long = 60_000L,
        baseTs: Long = BASE_TEST_TIME,
        nodeId: String = DEFAULT_NODE
    ): List<HLC> {
        return List(size) { index ->
            HLC(ts = baseTs + (index * stepMs), count = 0, nodeId = nodeId)
        }
    }

    /**
     * Generates a list of HLCs with identical physical time but incrementing logical counters.
     * Simulates high-frequency concurrent mutations happening on a single device within the same ms tick.
     */
    fun concurrentSequence(
        size: Int,
        ts: Long = BASE_TEST_TIME,
        nodeId: String = DEFAULT_NODE
    ): List<HLC> {
        return List(size) { index ->
            HLC(ts = ts, count = index, nodeId = nodeId)
        }
    }
}