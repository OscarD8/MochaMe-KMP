package com.mochame.sync.di.api

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.logger.test.TestLoggerModule
import com.mochame.node.di.StaggeredDbRetryPolicyModule
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.node.fixtures.SpyBootStatusManager
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.FakeTransactionProvider
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.api.boot.BootState
import com.mochame.sync.api.hlc.HLC
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.common.InternalTestApi
import com.mochame.sync.di.codec.CodecTestModule
import com.mochame.sync.di.fixtures.SyncInternalFixturesModule
import com.mochame.sync.di.infrastructure.DefaultKeyedLockerModule
import com.mochame.sync.fixtures.FakeBlobStore
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.fixtures.di.FixturesSyncModule
import com.mochame.sync.infrastructure.DefaultKeyedLocker
import com.mochame.sync.internal.fixtures.FeatureRepository
import com.mochame.sync.internal.fixtures.SpyHlcFactory
import com.mochame.sync.internal.fixtures.SpySyncWorkerHook
import com.mochame.sync.internal.fixtures.serialization.FakeFeatureCodec
import com.mochame.sync.internal.fixtures.serialization.FeatureCodecRouter
import com.mochame.sync.internal.fixtures.serialization.FeatureCodecRouterFixture
import com.mochame.sync.internal.fixtures.serialization.FeatureCodecV1
import com.mochame.sync.spi.infrastructure.BufferProvider
import com.mochame.utils.fixtures.FakeTimeUtils
import com.mochame.utils.fixtures.TestNodeId
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Test Fixture & Verification:
 *
 * Faked / Test-controlled dependencies:
 * - HlcFactory: Spy implementation around real clock to verify tick counts, causal timestamps, and floor tracking.
 * - TransactionProvider: In-memory passthrough with fault-injection (_shouldThrow) to assert transaction boundaries.
 * - SyncIntentStore: In-memory store (LinkedHashMap) to assert recorded intent payloads, metadata, and status.
 * - SyncWorkerHook: Spy implementation to verify invalidation signals dispatched strictly post-commit.
 * - NodeContextManager & BootStatusProvider: StateFlow-driven fakes to simulate boot states and node state.
 * - FeatureCodecRouterFixture & FakeFeatureCodec: Routes as default to lightweight fake. Decode / Encode pipeline verified
 *   2-byte preset completed roundtrip with parity.
 * - FakeBlobStore (holding FakeDigestState + TestWorkspace): Simulate throwing on each operation, and uses internal maps
 *   as opposed to involving the Test Filesystem
 *
 * Integrated Dependencies:
 * - ExecutionPolicy (StaggeredDbRetryPolicy): Validates retry loops on Transient.DatabaseBusy and ensures
 *   repository behavior despite non-atomic retry behaviors. Essential to verify against possibility of partial
 *   state updates within retry increments.
 * - KeyedLocker (DefaultKeyedLocker): Tightly coupled to functioning of repository. Mutex is required as the nested block suspends - faking unnecessary.
 */

@KoinApplication(modules = [LocalFirstRepoTestModule::class])
object LocalFirstRepoTestApp

@Module(
    includes = [
        FixturesSyncModule::class,
        SyncInternalFixturesModule::class,
        DefaultKeyedLockerModule::class,
        CodecTestModule::class,
        FixturesNodeModule::class,
        FixturesPlatformModule::class,
        StaggeredDbRetryPolicyModule::class,
        TestLoggerModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.api")
internal class LocalFirstRepoTestModule {

    @OptIn(InternalTestApi::class)
    @Single
    fun provideFeatureRepository(
        featureContext: FeatureContext = FeatureContext.TEST_STUB_A,
        deps: LocalFirstDependencies,
        codecRouter: FeatureCodecRouterFixture,
        logger: Logger,
    ): FeatureRepository = FeatureRepository(
        featureContext = featureContext,
        deps = deps,
        codecRouter = codecRouter,
        logger = logger
    )
}

@Factory
@ExperimentalKermitApi
internal class LocalFirstRepoTestEnv(
    val repo: FeatureRepository,
    val hlcFactory: SpyHlcFactory,
    val intentStore: FakeSyncIntentStore,
    val workerHook: SpySyncWorkerHook,
    val nodeManager: FakeNodeContextManager,
    val bootProvider: SpyBootStatusManager,
    val integratedCodec: FeatureCodecV1,
    val deps: LocalFirstDependencies,
    val blobStore: FakeBlobStore,
    val transactor: FakeTransactionProvider,
    val fakeClock: FakeTimeUtils,
    val locker: DefaultKeyedLocker,
    val fakeBufferProvider: BufferProvider,
    val logger: Logger,
    val writer: TestLogWriter,
) {
    @OptIn(InternalTestApi::class)
    fun createCodecIntegratedRepo(
        featureContext: FeatureContext = FeatureContext.TEST_STUB_A
    ): FeatureRepository = FeatureRepository(
        featureContext = featureContext,
        deps = deps,
        codecRouter = FeatureCodecRouter(integratedCodec, logger),
        logger = logger
    )

    @OptIn(InternalTestApi::class)
    fun createIntegratedMultiThreadedRepo(
        logger: Logger,
        fakeBufferProvider: BufferProvider,
        featureContext: FeatureContext = FeatureContext.TEST_STUB_A
    ): FeatureRepository = FeatureRepository(
        featureContext = featureContext,
        deps = deps.copy(ioContext = Dispatchers.Default),
        codecRouter = FeatureCodecRouterFixture(
            integratedCodec,
            FakeFeatureCodec(fakeBufferProvider),
            logger
        ),
        logger = logger
    )

    suspend fun setupValidContext(hlc: HLC? = null) {
        hlcFactory.hydrate(hlc, TestNodeId.A)
        bootProvider.updateBootState(BootState.Ready)
    }
}