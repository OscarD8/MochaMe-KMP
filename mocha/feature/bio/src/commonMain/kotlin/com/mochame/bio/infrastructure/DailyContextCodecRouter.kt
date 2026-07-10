package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.api.serialization.BaseFeatureCodecRouter
import com.mochame.sync.api.serialization.FeatureCodecRouter
import org.koin.core.annotation.Single

@Single(binds = [FeatureCodecRouter::class])
internal class DailyContextCodecRouter(
    v1: DailyContextCodecV1,
    logger: Logger
) : BaseFeatureCodecRouter<DailyContext>(
    versionRegistry = arrayOf(null, v1),
    latestVersion = 1,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.BIO, "DayRtr")
)