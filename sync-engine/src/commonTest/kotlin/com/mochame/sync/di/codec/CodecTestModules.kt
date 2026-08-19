package com.mochame.sync.di.codec

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.infrastructure.serialization.BatchCodecV1
import com.mochame.sync.infrastructure.serialization.DefaultPayloadCodec
import com.mochame.sync.infrastructure.serialization.IntentCodecV1
import com.mochame.sync.internal.fixtures.serialization.FakeBatchCodec
import com.mochame.sync.internal.fixtures.serialization.FakeIntentCodec
import com.mochame.sync.internal.fixtures.serialization.FeatureCodecRouterFixture
import com.mochame.sync.spi.infrastructure.serialization.BatchCodecRouter
import com.mochame.sync.spi.infrastructure.serialization.IntentCodecRouter
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [CodecTestModule::class])
internal object CodecTestApp

@KoinApplication(modules = [CodecFixtureModule::class])
internal object CodecFixtureTestApp


@Module(includes = [TestLoggerModule::class, FixturesPlatformModule::class])
@ComponentScan("com.mochame.sync.internal.fixtures.serialization")
internal class CodecTestModule

@Module(includes = [TestLoggerModule::class, FixturesPlatformModule::class, SyncInfraModule::class])
@ComponentScan("com.mochame.sync.internal.fixtures.serialization", "com.mochame.sync.di.codec")
internal class CodecFixtureModule


@ExperimentalKermitApi
@Factory
internal class CodecFixtureTestEnv(
    val featureRouter: FeatureCodecRouterFixture,
    val intentRouter: IntentCodecRouter,
    val realIntentCodec: IntentCodecV1,
    val fakeIntentCodec: FakeIntentCodec,
    val realBatchCodec: BatchCodecV1,
    val fakeBatchCodec: FakeBatchCodec,
    val batchRouter: BatchCodecRouter,
    val realPayloadCodec: DefaultPayloadCodec,
    val writer: TestLogWriter,
    val logger: Logger
)