package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.serialization.BaseFeatureCodecRouter
import com.mochame.sync.contract.serialization.FeatureCodecRouter
import org.koin.core.annotation.Single

@Single(binds = [FeatureCodecRouter::class])
internal class DailyContextCodecRouter(
    v1: DailyContextCodecV1,
    logger: Logger
) : BaseFeatureCodecRouter<DailyContext>(
    versionRegistry = arrayOf(null, v1),
    latestVersion = 0x01,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.BIO, "DayRtr")
)