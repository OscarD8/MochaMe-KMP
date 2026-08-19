package com.mochame.sync.internal.fixtures.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodecRouter
import org.koin.core.annotation.Single

@Single
class FeatureCodecRouterFixture(
    val v1: FeatureCodecV1,
    val v2: FakeFeatureCodec,
    logger: Logger
) : BaseFeatureCodecRouter<FeatureEntity>(
    versionRegistry = arrayOf(null, v1, v2),
    latestVersion = 2,
    logger = logger.withTags(LogTags.Layer.SERI, LogTags.Domain.SYNC, "TeCRtr")
)

@Single
class FeatureCodecRouter(
    val v1: FeatureCodecV1,
    logger: Logger
) : BaseFeatureCodecRouter<FeatureEntity>(
    versionRegistry = arrayOf(null, v1),
    latestVersion = 1,
    logger = logger.withTags(LogTags.Layer.SERI, LogTags.Domain.SYNC, "TeCRtr")
)

