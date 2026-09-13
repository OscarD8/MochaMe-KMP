package com.mochame.sync.api.repository

import co.touchlab.kermit.Logger
import com.mochame.annotations.IoContext
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.hlc.HlcFactory
import com.mochame.sync.spi.infrastructure.BlobStore
import com.mochame.sync.spi.infrastructure.KeyedLocker
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
data class LocalFirstDependencies(
    val hlcFactory: HlcFactory,
    val transactor: TransactionProvider,
    val blobStore: BlobStore,
    val intentStore: SyncIntentStore,
    val workerHook: SyncWorkerHook,
    val executor: ExecutionPolicy,
    val locker: KeyedLocker,
    val logger: Logger,
    val nodeManager: NodeContextManager,
    val bootProvider: BootStatusProvider,
    @IoContext val ioContext: CoroutineContext
)