package com.mochame.system.infra.data

import com.mochame.contract.node.NodeContext
import com.mochame.system.infra.data.NodeIdentityEntity

internal fun NodeIdentityEntity.toNodeContext() = NodeContext(
    nodeId = nodeId,
    baselineVersion = lastBootedAppVersion
)