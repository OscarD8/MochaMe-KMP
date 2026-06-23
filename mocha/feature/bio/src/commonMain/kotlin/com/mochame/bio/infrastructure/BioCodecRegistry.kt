package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.sync.contract.serialization.FeatureCodecRegistry
import com.mochame.sync.contract.serialization.FeatureRoutingRegistry
import org.koin.core.annotation.Single

@Single(binds = [FeatureCodecRegistry::class])
internal class BioCodecRegistry(
    v1: BioCodecV1,
    logger: Logger
) : FeatureRoutingRegistry<DailyContext>(
    codecMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger
)