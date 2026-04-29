package com.mochame.system.orchestrator

import co.touchlab.kermit.Logger
import com.mochame.contract.di.IdentityMutex
import com.mochame.contract.di.IoContext
import com.mochame.contract.identity.GlobalMetadataStore
import com.mochame.contract.identity.IdGenerator
import com.mochame.contract.identity.IdentityManager
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Module
class SystemOrchestratorModule {
    @Single
    @IdentityMutex
    fun provideIdentityMutex(): Mutex = Mutex()

    @Single(binds = [IdentityManager::class])
    fun provideIdentityManager(
        metadataStore: GlobalMetadataStore,
        idGenerator: IdGenerator,
        @IoContext ioContext: CoroutineContext,
        @IdentityMutex mutex: Mutex,
        logger: Logger
    ): RealIdentityManager = RealIdentityManager(
        metadataStore = metadataStore,
        idGenerator = idGenerator,
        ioContext = ioContext,
        mutex = mutex,
        logger = logger
    )
}