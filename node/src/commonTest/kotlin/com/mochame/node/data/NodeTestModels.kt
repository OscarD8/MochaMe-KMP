package com.mochame.node.data

import com.mochame.support.TestHlcFactory
import com.mochame.sync.api.models.HLC
import com.mochame.sync.spi.node.NodeContext

/**
 * Generates a deterministic NodeContextEntity with standard test defaults.
 */
fun createTestNodeContextEntity(
    nodeId: String = TestHlcFactory.DEFAULT_NODE,
    appVersion: Int = 1,
    createdAt: Long = TestHlcFactory.BASE_TEST_TIME,
    lastServerWatermark: String? = null,
    maxHlc: String? = null,
    lastServerSyncTime: Long? = null,
    lastLocalMutationTime: Long? = null
): NodeContextEntity = NodeContextEntity(
    id = 1,
    nodeId = nodeId,
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)

/**
 * Generates a deterministic domain NodeContext with standard test defaults.
 */
fun createTestNodeContext(
    nodeId: String = TestHlcFactory.DEFAULT_NODE,
    appVersion: Int = 1,
    createdAt: Long = TestHlcFactory.BASE_TEST_TIME,
    lastServerWatermark: String? = null,
    maxHlc: HLC? = null,
    lastServerSyncTime: Long? = null,
    lastLocalMutationTime: Long? = null
): NodeContext = NodeContext(
    nodeId = nodeId,
    appVersion = appVersion,
    createdAt = createdAt,
    lastServerWatermark = lastServerWatermark,
    maxHlc = maxHlc,
    lastServerSyncTime = lastServerSyncTime,
    lastLocalMutationTime = lastLocalMutationTime
)