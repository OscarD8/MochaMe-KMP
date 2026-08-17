package com.mochame.sync.domain.model

import com.mochame.sync.api.metadata.FeatureContext

data class LockKey(
    val context: FeatureContext,
    val id: Long
)