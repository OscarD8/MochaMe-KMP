package com.mochame.logic.fixtures

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.di.IoContext
import com.mochame.contract.boot.BootStatusProvider
import com.mochame.contract.boot.BootStatusUpdater
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.fixtures.di.MetadataFixtureModule
import com.mochame.logic.BootStatusManager
import com.mochame.logic.IdentityManager
import com.mochame.contract.identity.IdGenerator
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Provides trackable ID Generation and a metadata store with no database interaction.
 */
@Module(includes = [MetadataFixtureModule::class])
class OrchestratorFixtureModule {

    @Single
    @IdentityMutex
    fun provideIdentityMutex(): Mutex = Mutex()

    @Single(binds = [BootStatusProvider::class, BootStatusUpdater::class])
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
        ioContext = workContext,
        mutex = mutex,
        logger = logger
    )

}