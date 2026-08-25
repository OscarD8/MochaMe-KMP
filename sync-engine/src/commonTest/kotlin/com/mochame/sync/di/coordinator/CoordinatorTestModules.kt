@file:OptIn(InternalTestApi::class)

package com.mochame.sync.di.coordinator

import com.mochame.node.di.StaggeredDbRetryPolicyModule
import com.mochame.node.fixtures.FakeNodeContextManager
import com.mochame.node.fixtures.SpyBootStatusManager
import com.mochame.node.fixtures.di.FixturesNodeModule
import com.mochame.platform.fixtures.FakeTransactionProvider
import com.mochame.platform.fixtures.di.FixturesPlatformModule
import com.mochame.sync.api.metadata.FeatureContext
import com.mochame.sync.api.metadata.SyncStatus
import com.mochame.sync.common.InternalTestApi
import com.mochame.sync.di.SyncConcurrencyModule
import com.mochame.sync.di.SyncOrchestrationModule
import com.mochame.sync.di.fixtures.SyncInternalFixturesModule
import com.mochame.sync.fixtures.FakeSyncIntentStore
import com.mochame.sync.internal.fixtures.FakeSyncReceiver
import com.mochame.sync.internal.fixtures.SpyHlcFactory
import com.mochame.sync.internal.fixtures.SpySyncWorkerHook
import com.mochame.sync.internal.fixtures.serialization.FakePayloadCodec
import com.mochame.sync.orchestration.SyncCoordinator
import com.mochame.sync.spi.infrastructure.SyncReceiver
import com.mochame.sync.spi.infrastructure.serialization.PayloadCodec
import com.mochame.sync.spi.node.NodeContextManager
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@KoinApplication(modules = [CoordinatorTestModules::class])
object SyncCoordinatorTestApp

@Module(
    includes = [
        SyncOrchestrationModule::class,
        SyncInternalFixturesModule::class,
        SyncConcurrencyModule::class,
        FixturesPlatformModule::class,
        FixturesNodeModule::class,
        StaggeredDbRetryPolicyModule::class
    ]
)
@ComponentScan("com.mochame.sync.di.coordinator")
class CoordinatorTestModules {

    @Single(binds = [PayloadCodec::class])
    fun provideFakePayloadCodec(): FakePayloadCodec = FakePayloadCodec()

    @Named("stubA")
    @Single(binds = [SyncReceiver::class, FakeSyncReceiver::class])
    fun provideFakeSyncReceiverA(): FakeSyncReceiver = FakeSyncReceiver(FeatureContext.TEST_STUB_A)

    @Named("stubB")
    @Single(binds = [SyncReceiver::class, FakeSyncReceiver::class])
    fun provideFakeSyncReceiverB(): FakeSyncReceiver = FakeSyncReceiver(FeatureContext.TEST_STUB_B)
}

@Factory
internal class SyncCoordinatorTestEnv(
    val coordinator: SyncCoordinator,
    @Named("stubA") val stubA: FakeSyncReceiver,
    @Named("stubB") val stubB: FakeSyncReceiver,
    val intentStore: FakeSyncIntentStore,
    val codec: FakePayloadCodec,
    val hlcFactory: SpyHlcFactory,
    val transactor: FakeTransactionProvider,
    val workerHook: SpySyncWorkerHook,
    val bootManager: SpyBootStatusManager,
    val nodeManager: FakeNodeContextManager
) {
    fun assertIntentsProperlyBatched(expectedKeys: Set<Long>) {
        val storedIntents = intentStore.intents
        val encodedIntents = codec.encodedInvocations.flatten()

        val storedKeys = storedIntents.map { it.candidateKey }.toSet()
        val encodedKeys = encodedIntents.map { it.candidateKey }.toSet()

        assertEquals(
            expectedKeys,
            storedKeys,
            "All seeded candidateKeys must exist in the intent store"
        )
        assertEquals(
            expectedKeys,
            encodedKeys,
            "All seeded candidateKeys must have been encoded across batch sweeps"
        )
        assertEquals(
            expectedKeys.size,
            encodedIntents.size,
            "Total encoded intents count must match seeded count exactly"
        )

        storedIntents.forEach { intent ->
            assertEquals(
                SyncStatus.SYNCING,
                intent.syncStatus,
                "Intent for key ${intent.candidateKey} must be in SYNCING status"
            )
            assertNotNull(
                intent.syncId,
                "Intent for key ${intent.candidateKey} must hold a non-null batchId (syncId)"
            )
            assertNotNull(
                intent.leasedAt,
                "Intent for key ${intent.candidateKey} must hold a valid leasedAt timestamp"
            )
        }
    }
}