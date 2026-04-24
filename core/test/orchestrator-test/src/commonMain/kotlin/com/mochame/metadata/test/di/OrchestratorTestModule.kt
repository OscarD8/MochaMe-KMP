package com.mochame.metadata.test.di

import co.touchlab.kermit.Logger
import com.mochame.di.IdentityMutex
import com.mochame.di.IoContext
import com.mochame.metadata.BootStatusProvider
import com.mochame.metadata.BootStatusUpdater
import com.mochame.metadata.GlobalMetadataStore
import com.mochame.metadata.test.FakeGlobalMetaStore
import com.mochame.metadata.test.FakeIdGenerator
import com.mochame.orchestrator.BootStatusManager
import com.mochame.orchestrator.IdentityManager
import com.mochame.utils.IdGenerator
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Provides trackable ID Generation and a metadata store with no database interaction.
 */
@Module
class OrchestratorTestModule {

    @Single
    @IdentityMutex
    fun provideIdentityMutex(): Mutex = Mutex()

    @Single
    fun provideMetaStore(): GlobalMetadataStore = FakeGlobalMetaStore()

    @Single
    fun provideIdGenerator(): IdGenerator = FakeIdGenerator()

    @Single(
        binds = [
            BootStatusProvider::class,
            BootStatusUpdater::class
        ]
    )
    fun provideBootStatusManager(): BootStatusManager = BootStatusManager()

    // -----------------------------------------------------------
    // STUBS
    // -----------------------------------------------------------
    @Single
    fun stubLogger(): Logger = error("Provided at runtime")

    @Single
    @IoContext
    fun stubIoContext(): CoroutineContext = EmptyCoroutineContext

    @Single
    fun provideIdentityManager(
        metadataStore: GlobalMetadataStore,         // Satisfied by provideMetaStore()
        idGenerator: IdGenerator,                   // Satisfied by provideIdGenerator()
        @IoContext workContext: CoroutineContext,   // Satisfied by stubIoContext()
        @IdentityMutex mutex: Mutex,                // Satisfied by provideIdentityMutex()
        logger: Logger                              // Satisfied by stubLogger()
    ): IdentityManager = IdentityManager(
        metadataStore = metadataStore,
        idGenerator = idGenerator,
        workContext = workContext,
        mutex = mutex,
        logger = logger
    )

}