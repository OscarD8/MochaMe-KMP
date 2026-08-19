package com.mochame.sync.di.api

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.TestLogWriter
import com.mochame.node.di.StaggeredDbRetryPolicyModule
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.support.TestSupportModule
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.di.SyncInfraModule
import com.mochame.sync.di.codec.CodecTestModule
import com.mochame.sync.di.fixtures.SyncInternalFixturesModule
import com.mochame.sync.common.InternalTestApi
import com.mochame.sync.fixtures.di.FixturesSyncModule
import com.mochame.sync.internal.fixtures.FeatureRepository
import com.mochame.sync.internal.fixtures.serialization.FeatureCodecRouterFixture
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
 *
 * Integrated Dependencies:
 * - ExecutionPolicy (StaggeredDbRetryPolicy): Validates retry loops on Transient.DatabaseBusy and ensures
 *   repository behavior despite non-atomic retry behaviors. Essential to verify against possibility of partial
 *   state updates within retry increments.
 * - KeyedLocker (DefaultKeyedLocker): Tightly coupled to functioning of repository. Mutex is required as the nested block suspends - faking unnecessary.
 * - BlobStore (DefaultBlobStore holding FakeDigestState + TestWorkspace): Validates payload size routing (>64KB), staging, committing,
 *   and abort pipelines using test infrastructure (file paths).
 */

@KoinApplication(modules = [LocalFirstRepoTestModule::class])
object LocalFirstRepoTestApp

@Module(
    includes = [
        FixturesSyncModule::class,
        SyncInternalFixturesModule::class,
        CodecTestModule::class,
        FixturesNodeModule::class,
        FixturesPlatformModule::class,
        StaggeredDbRetryPolicyModule::class
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
    val deps: LocalFirstDependencies,
    val writer: TestLogWriter,
)