package com.mochame.bio.infrastructure

import co.touchlab.kermit.Logger
import com.mochame.bio.domain.DailyContext
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.contract.serialization.BaseFeatureCodecRouter
import org.koin.core.annotation.Single

@Single
internal class DailyContextCodecRouter(
    v1: DailyContextCodecV1,
    logger: Logger
) : BaseFeatureCodecRouter<DailyContext>(
    versionMap = mapOf(0x01.toByte() to v1),
    latestVersion = 0x01,
    logger = logger.withTags(LogTags.Layer.INFRA, LogTags.Domain.BIO, "DayRtr")
)