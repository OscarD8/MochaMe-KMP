package com.mochame.app.assembly.di

import co.touchlab.kermit.Logger
import com.mochame.annotations.IoContext
import com.mochame.bio.di.BioProductionModule
import com.mochame.logger.LoggerModule
import com.mochame.node.di.NodeProductionModule
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.api.repository.LocalFirstDependencies
import com.mochame.sync.di.SyncProductionModule
import com.mochame.sync.spi.infrastructure.BlobStore
import com.mochame.sync.spi.infrastructure.KeyedLocker
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Module(
    includes = [
        LoggerModule::class,
        SyncProductionModule::class,
        NodeProductionModule::class,
        MochaSchemaModule::class,
        BioProductionModule::class,
    ]
)
@ComponentScan("com.mochame.app.assembly")
class MochaAssemblyModule {

    @Single(binds = [LocalFirstDependencies::class])
    fun provideLocalFirstDependencies(
        @Provided hlcFactory: HlcFactory,
        transactor: TransactionProvider,
        @Provided blobStore: BlobStore,
        @Provided intentStore: SyncIntentStore,
        @Provided workerHook: SyncWorkerHook,
        @Provided executor: ExecutionPolicy,
        @Provided locker: KeyedLocker,
        logger: Logger,
        @Provided nodeManager: NodeContextManager,
        @Provided bootStatus: BootStatusProvider,
        @IoContext ioContext: CoroutineContext
    ): LocalFirstDependencies = LocalFirstDependencies(
        hlcFactory = hlcFactory,
        transactor = transactor,
        blobStore = blobStore,
        intentStore = intentStore,
        workerHook = workerHook,
        executor = executor,
        locker = locker,
        logger = logger,
        nodeManager = nodeManager,
        bootProvider = bootStatus,
        ioContext = ioContext
    )
}