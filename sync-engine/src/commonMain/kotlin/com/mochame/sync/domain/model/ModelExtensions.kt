package com.mochame.sync.domain.model

import com.mochame.sync.spi.models.DecodeContext
import com.mochame.sync.spi.models.SyncIntent

internal fun SyncIntent.deriveContext() = DecodeContext(
    featureSchemaVersion,
    candidateKey,
    hlc,
    operation,
    overflowBlobId,
    changedMask
)