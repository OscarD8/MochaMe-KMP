package com.mochame.sync.di.codec

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.platform.fixtures.di.FakeBufferProviderModule
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.domain.serialization.BatchCodecRouter
import com.mochame.sync.domain.serialization.IntentCodecRouter
import com.mochame.sync.fixtures.serialization.FakeBatchCodec
import com.mochame.sync.fixtures.serialization.FakeIntentCodec
import com.mochame.sync.fixtures.serialization.FeatureCodecRouter
import com.mochame.sync.fixtures.serialization.FeatureCodecRouterFixture
import com.mochame.sync.fixtures.serialization.FeatureCodecV1
import com.mochame.sync.infrastructure.serialization.BatchCodecV1
import com.mochame.sync.infrastructure.serialization.DefaultPayloadCodec
import com.mochame.sync.infrastructure.serialization.IntentCodecV1
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [CodecTestModule::class])
internal class CodecTestApp

@KoinApplication(modules = [CodecFixtureModule::class])
internal class CodecFixtureTestApp

@KoinApplication(modules = [CodecIntegrationTestModule::class])
internal class CodecIntegrationTestApp


@Module(includes = [TestLoggerModule::class, FakeBufferProviderModule::class])
@ComponentScan( "com.mochame.sync.fixtures.serialization")
internal class CodecTestModule

@Module(includes = [TestLoggerModule::class, FakeBufferProviderModule::class, SyncInfraModule::class])
@ComponentScan( "com.mochame.sync.fixtures.serialization", "com.mochame.sync.di.codec")
internal class CodecFixtureModule

@Module(includes = [TestLoggerModule::class, FakeBufferProviderModule::class, SyncInfraModule::class])
@ComponentScan( "com.mochame.sync.fixtures.serialization", "com.mochame.sync.di.codec")
internal class CodecIntegrationTestModule


@ExperimentalKermitApi
@Factory
internal class CodecIntegrationTestEnv(
    val featureCodec: FeatureCodecV1,
    val featureRouter: FeatureCodecRouter,
    val intentRouter: IntentCodecRouter,
    val intentCodec: IntentCodecV1,
    val batchCodec: BatchCodecV1,
    val batchRouter: BatchCodecRouter,
    val payloadCodec: DefaultPayloadCodec,
    val writer: TestLogWriter,
    val logger: Logger
)

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