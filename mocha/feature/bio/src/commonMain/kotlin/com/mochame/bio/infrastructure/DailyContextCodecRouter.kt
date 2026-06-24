package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.sync.contract.serialization.FeatureCodecRouter
import org.koin.core.annotation.Single

@Single
internal class DailyContextCodecRouter(
    v1: DailyContextCodecV1,
    logger: Logger
) : FeatureCodecRouter<DailyContext>(
    versionMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger
)