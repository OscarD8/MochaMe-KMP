package com.mochame.sync.fixtures.serialization

import co.touchlab.kermit.Logger
import com.mochame.logger.LogTags
import com.mochame.logger.withTags
import com.mochame.sync.spi.infrastructure.serialization.BaseFeatureCodecRouter
import com.mochame.sync.spi.infrastructure.serialization.FeatureCodec
import org.koin.core.annotation.Single

@Single
internal class TestEntityCodecRouter(
    v1: TestEntityCodecV1,
    v2: TestEntityCodecV2,
    logger: Logger
) : BaseFeatureCodecRouter<TestEntity>(
    versionRegistry = arrayOf(null, v1, v2),
    latestVersion = 2,
    logger = logger.withTags(LogTags.Layer.SERI, LogTags.Domain.SYNC, "TeCRtr")
)