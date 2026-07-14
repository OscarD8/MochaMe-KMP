package com.mochame.sync.api.repository

import co.touchlab.kermit.Logger
import com.mochame.annotations.IoContext
import com.mochame.sync.api.boot.BootStatusProvider
import com.mochame.sync.api.infrastructure.HlcFactory
import com.mochame.sync.spi.infrastructure.BlobStager
import com.mochame.sync.spi.infrastructure.KeyedLocker
import com.mochame.sync.spi.infrastructure.SyncIntentStore
import com.mochame.sync.spi.infrastructure.SyncWorkerHook
import com.mochame.sync.spi.infrastructure.TransactionProvider
import com.mochame.sync.spi.node.NodeContextManager
import com.mochame.sync.spi.policy.ExecutionPolicy
import kotlin.coroutines.CoroutineContext
import org.koin.core.annotation.Single

@Single
class LocalFirstDependencies(
    val hlcFactory: HlcFactory,
    val transactor: TransactionProvider,
    val blobStager: BlobStager,
    val intentStore: SyncIntentStore,
    val invalidationHook: SyncWorkerHook,
    val executor: ExecutionPolicy,
    val locker: KeyedLocker,
    val logger: Logger,
    val nodeManager: NodeContextManager,
    val bootStatus: BootStatusProvider,
    @IoContext val ioContext: CoroutineContext
)